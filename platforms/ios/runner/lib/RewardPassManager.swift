import Foundation

// Port of Android's System/RewardPassManager; the public state and reward rules stay identical.
final class RewardPassManager {
    private static let hour: Int64 = 60 * 60 * 1000
    private static let maximumDuration = 10 * hour
    private static let maximumRedemptions = 3
    private let store: SecretStore
    private let now: () -> Date
    private let focusHours: () -> Int

    init(store: SecretStore = SecretStore(namespace: "ads"), now: @escaping () -> Date = Date.init,
         focusHours: @escaping () -> Int = { Int.random(in: 4...6) }) {
        self.store = store
        self.now = now
        self.focusHours = focusHours
    }

    func getRewardStatus() throws -> String {
        let date = now()
        var state = try synchronizedState(date)
        let status = status(state, date: date)
        state.expiryNoticePendingUntil = 0
        try save(state)
        return try json(status)
    }

    func redeemReward(_ offerID: String) throws -> String {
        let date = now()
        let time = milliseconds(date)
        var state = try synchronizedState(date)
        let reason = disabledReason(state, time: time)
        guard reason.isEmpty else { throw SecretFailure(reason) }
        let duration: Int64
        switch offerID {
        case "quick": duration = Self.hour
        case "focus": duration = Int64(focusHours()) * Self.hour
        default: throw SecretFailure("Unknown reward offer.")
        }
        let baseTime = max(time, state.adFreeUntil)
        state.adFreeUntil = min(baseTime + duration, time + Self.maximumDuration)
        state.lastExpiredRewardUntil = 0
        state.expiryNoticePendingUntil = 0
        state.redemptionDay = day(date)
        state.redemptionsToday += 1
        try save(state)
        var result = status(state, date: date)
        result["grantedDurationMs"] = duration
        result["appliedDurationMs"] = max(0, state.adFreeUntil - baseTime)
        result["offerId"] = offerID
        return try json(result)
    }

    private func synchronizedState(_ date: Date) throws -> RewardPassState {
        let raw = try store.get("reward_state")
        let values = (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [String: Any] ?? [:]
        var state = RewardPassState(values, today: day(date))
        if state.redemptionDay != day(date) {
            state.redemptionDay = day(date)
            state.redemptionsToday = 0
        }
        if state.adFreeUntil > 0, state.adFreeUntil <= milliseconds(date) {
            state.expiryNoticePendingUntil = state.adFreeUntil
            state.lastExpiredRewardUntil = state.adFreeUntil
            state.adFreeUntil = 0
        }
        return state
    }

    private func status(_ state: RewardPassState, date: Date) -> [String: Any] {
        let time = milliseconds(date)
        let reason = disabledReason(state, time: time)
        return ["adFreeUntil": state.adFreeUntil, "lastExpiredRewardUntil": state.lastExpiredRewardUntil,
                "isActive": state.adFreeUntil > time, "remainingMs": max(0, state.adFreeUntil - time),
                "redemptionsToday": state.redemptionsToday,
                "remainingRedemptions": max(0, Self.maximumRedemptions - state.redemptionsToday),
                "maxRedemptionsPerDay": Self.maximumRedemptions, "maxActivePassMs": Self.maximumDuration,
                "hasPendingExpiryNotice": state.expiryNoticePendingUntil > 0,
                "expiryNoticePendingUntil": state.expiryNoticePendingUntil,
                "canRedeem": reason.isEmpty, "redeemDisabledReason": reason]
    }

    private func disabledReason(_ state: RewardPassState, time: Int64) -> String {
        if state.redemptionsToday >= Self.maximumRedemptions {
            return "Daily limit reached. You can redeem up to 3 rewards per day."
        }
        if state.adFreeUntil - time >= Self.maximumDuration {
            return "You already have the maximum 10 hours of ad-free time active."
        }
        return ""
    }

    private func save(_ state: RewardPassState) throws {
        try store.set("reward_state", value: json([
            "adFreeUntil": state.adFreeUntil, "lastExpiredRewardUntil": state.lastExpiredRewardUntil,
            "expiryNoticePendingUntil": state.expiryNoticePendingUntil,
            "redemptionDay": state.redemptionDay, "redemptionsToday": state.redemptionsToday,
        ]))
    }

    private func json(_ value: [String: Any]) throws -> String {
        String(decoding: try JSONSerialization.data(withJSONObject: value), as: UTF8.self)
    }

    private func milliseconds(_ date: Date) -> Int64 { Int64(date.timeIntervalSince1970 * 1000) }

    private func day(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}

private struct RewardPassState {
    var adFreeUntil: Int64
    var lastExpiredRewardUntil: Int64
    var expiryNoticePendingUntil: Int64
    var redemptionDay: String
    var redemptionsToday: Int

    init(_ values: [String: Any], today: String) {
        adFreeUntil = (values["adFreeUntil"] as? NSNumber)?.int64Value ?? 0
        lastExpiredRewardUntil = (values["lastExpiredRewardUntil"] as? NSNumber)?.int64Value ?? 0
        expiryNoticePendingUntil = (values["expiryNoticePendingUntil"] as? NSNumber)?.int64Value ?? 0
        redemptionDay = values["redemptionDay"] as? String ?? today
        redemptionsToday = (values["redemptionsToday"] as? NSNumber)?.intValue ?? 0
    }
}
