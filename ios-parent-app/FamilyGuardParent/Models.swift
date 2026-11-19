import Foundation

// MARK: - API response models (match the Android LocalApiServer JSON)

struct AuthResponse: Decodable {
    let token: String
    let expiresInSec: Int
}

struct AppUsage: Decodable, Identifiable {
    var id: String { pkg }
    let label: String
    let pkg: String
    let totalMs: Int64
    let launches: Int
}

struct LocationFix: Decodable, Identifiable {
    var id: Int64 { timestamp }
    let timestamp: Int64
    let lat: Double
    let lon: Double
    let accuracyM: Double
}

struct Summary: Decodable {
    let deviceModel: String
    let androidVersion: String
    let generatedAt: Int64
    let todayScreenTimeMs: Int64
    let topAppsToday: [AppUsage]
    let lastLocation: LocationFix?
    let usageAccessGranted: Bool
}

struct DayUsage: Decodable, Identifiable {
    var id: Int64 { dayEpoch }
    let dayEpoch: Int64
    let totalMs: Int64
    let apps: [AppUsage]
}

struct UsageResponse: Decodable { let days: [DayUsage] }

struct WebVisit: Decodable, Identifiable {
    var id: String { "\(timestamp)-\(url)" }
    let timestamp: Int64
    let url: String
    let host: String
    let browser: String
}

struct WebResponse: Decodable { let visits: [WebVisit] }
struct LocationsResponse: Decodable { let locations: [LocationFix] }

// MARK: - Formatting helpers

enum Fmt {
    static func duration(_ ms: Int64) -> String {
        let totalMin = Int(ms / 60000)
        let h = totalMin / 60, m = totalMin % 60
        return h > 0 ? "\(h)h \(m)m" : "\(m)m"
    }
    static func time(_ epochMs: Int64) -> String {
        let d = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let f = DateFormatter(); f.dateStyle = .short; f.timeStyle = .short
        return f.string(from: d)
    }
    static func day(_ epochMs: Int64) -> String {
        let d = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let f = DateFormatter(); f.dateFormat = "EEE, MMM d"
        return f.string(from: d)
    }
}
