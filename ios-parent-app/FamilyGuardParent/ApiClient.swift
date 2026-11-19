import Foundation

enum ApiError: LocalizedError {
    case badURL
    case invalidPin
    case unauthorized
    case server(Int)
    case network(String)
    case decode

    var errorDescription: String? {
        switch self {
        case .badURL: return "The address looks invalid. Use the format shown on the child's phone, e.g. 192.168.1.20:8766."
        case .invalidPin: return "Incorrect PIN."
        case .unauthorized: return "Session expired. Please sign in again."
        case .server(let c): return "The child's phone returned an error (\(c))."
        case .network(let m): return "Could not reach the phone: \(m). Make sure both phones are on the same Wi-Fi."
        case .decode: return "Unexpected response from the child's phone."
        }
    }
}

/// Talks to the Android child app's local server over the LAN. No cloud.
@MainActor
final class ApiClient: ObservableObject {
    @Published var host: String = UserDefaults.standard.string(forKey: "fg_host") ?? ""
    @Published var isSignedIn: Bool = false

    private var token: String?

    private var base: String { "http://\(host)" }

    private let session: URLSession = {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 8
        cfg.waitsForConnectivity = false
        return URLSession(configuration: cfg)
    }()

    func signIn(hostInput: String, pin: String) async throws {
        let cleaned = normalizeHost(hostInput)
        guard !cleaned.isEmpty else { throw ApiError.badURL }
        self.host = cleaned
        UserDefaults.standard.set(cleaned, forKey: "fg_host")

        guard let url = URL(string: "http://\(cleaned)/api/auth") else { throw ApiError.badURL }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["pin": pin])

        let (data, resp): (Data, URLResponse)
        do { (data, resp) = try await session.data(for: req) }
        catch { throw ApiError.network(error.localizedDescription) }

        guard let http = resp as? HTTPURLResponse else { throw ApiError.decode }
        if http.statusCode == 401 { throw ApiError.invalidPin }
        guard http.statusCode == 200 else { throw ApiError.server(http.statusCode) }
        guard let auth = try? JSONDecoder().decode(AuthResponse.self, from: data) else { throw ApiError.decode }
        self.token = auth.token
        self.isSignedIn = true
    }

    func signOut() {
        token = nil
        isSignedIn = false
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        guard let token = token else { throw ApiError.unauthorized }
        guard let url = URL(string: base + path) else { throw ApiError.badURL }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let (data, resp): (Data, URLResponse)
        do { (data, resp) = try await session.data(for: req) }
        catch { throw ApiError.network(error.localizedDescription) }

        guard let http = resp as? HTTPURLResponse else { throw ApiError.decode }
        if http.statusCode == 401 { isSignedIn = false; throw ApiError.unauthorized }
        guard http.statusCode == 200 else { throw ApiError.server(http.statusCode) }
        guard let decoded = try? JSONDecoder().decode(T.self, from: data) else { throw ApiError.decode }
        return decoded
    }

    func summary() async throws -> Summary { try await get("/api/summary") }
    func usage(days: Int = 7) async throws -> UsageResponse { try await get("/api/usage?days=\(days)") }
    func web(days: Int = 7) async throws -> WebResponse { try await get("/api/web?days=\(days)") }
    func locations(days: Int = 3) async throws -> LocationsResponse { try await get("/api/locations?days=\(days)") }

    /// Accepts "192.168.1.20", "192.168.1.20:8766", or "http://192.168.1.20:8766".
    private func normalizeHost(_ input: String) -> String {
        var s = input.trimmingCharacters(in: .whitespacesAndNewlines)
        s = s.replacingOccurrences(of: "http://", with: "")
             .replacingOccurrences(of: "https://", with: "")
        if s.hasSuffix("/") { s.removeLast() }
        if !s.contains(":") { s += ":8766" }   // default server port
        return s
    }
}
