import SwiftUI

struct UsageView: View {
    @EnvironmentObject var api: ApiClient
    @State private var days: [DayUsage] = []
    @State private var error: String?
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Group {
                if let error, !loaded {
                    ErrorView(message: error) { Task { await load() } }
                } else if days.isEmpty && loaded {
                    ContentUnavailableView("No app usage yet",
                        systemImage: "apps.iphone",
                        description: Text("Usage will appear once the child uses apps with Usage Access granted."))
                } else {
                    List {
                        ForEach(days) { day in
                            Section("\(Fmt.day(day.dayEpoch)) · \(Fmt.duration(day.totalMs)) total") {
                                ForEach(day.apps.prefix(15).map { $0 }) { app in
                                    AppRow(app: app, maxMs: maxMs(day.apps))
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("App usage (7 days)")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func maxMs(_ apps: [AppUsage]) -> Int64 { max(1, apps.map { $0.totalMs }.max() ?? 1) }

    private func load() async {
        error = nil
        do { days = try await api.usage(days: 7).days }
        catch { self.error = error.localizedDescription }
        loaded = true
    }
}
