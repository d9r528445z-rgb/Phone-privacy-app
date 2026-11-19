import SwiftUI

struct WebHistoryView: View {
    @EnvironmentObject var api: ApiClient
    @State private var visits: [WebVisit] = []
    @State private var error: String?
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Group {
                if let error, !loaded {
                    ErrorView(message: error) { Task { await load() } }
                } else if visits.isEmpty && loaded {
                    ContentUnavailableView("No browsing history",
                        systemImage: "globe",
                        description: Text("Make sure the web-logging accessibility service is enabled on the child's phone."))
                } else {
                    List(visits) { v in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(v.host).fontWeight(.semibold)
                            Text(v.url).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            Text(Fmt.time(v.timestamp)).font(.caption2).foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            .navigationTitle("Web history (7 days)")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func load() async {
        error = nil
        do { visits = try await api.web(days: 7).visits }
        catch { self.error = error.localizedDescription }
        loaded = true
    }
}
