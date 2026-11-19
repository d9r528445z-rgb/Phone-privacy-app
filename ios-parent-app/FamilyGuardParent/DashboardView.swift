import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var api: ApiClient

    var body: some View {
        TabView {
            SummaryView().tabItem { Label("Summary", systemImage: "gauge.medium") }
            UsageView().tabItem { Label("App usage", systemImage: "apps.iphone") }
            WebHistoryView().tabItem { Label("Web", systemImage: "globe") }
            LocationView().tabItem { Label("Location", systemImage: "location") }
        }
    }
}

// MARK: - Summary

struct SummaryView: View {
    @EnvironmentObject var api: ApiClient
    @State private var summary: Summary?
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        NavigationStack {
            Group {
                if let s = summary {
                    List {
                        if !s.usageAccessGranted {
                            Section {
                                Text("Usage access is OFF on the child's phone, so app-time data is empty. Grant it on that phone in Family Guard → Set up permissions.")
                                    .font(.footnote).foregroundStyle(.orange)
                            }
                        }
                        Section("Screen time today") {
                            Text(Fmt.duration(s.todayScreenTimeMs))
                                .font(.system(size: 34, weight: .bold))
                        }
                        Section("Most used today") {
                            if s.topAppsToday.isEmpty {
                                Text("No usage recorded yet today.").foregroundStyle(.secondary)
                            } else {
                                ForEach(s.topAppsToday) { app in AppRow(app: app, maxMs: maxMs(s.topAppsToday)) }
                            }
                        }
                        if let loc = s.lastLocation {
                            Section("Last known location") {
                                LocationRow(fix: loc)
                            }
                        }
                        Section { Button("Sign out", role: .destructive) { api.signOut() } }
                    }
                } else if loading {
                    ProgressView()
                } else if let error {
                    ErrorView(message: error) { Task { await load() } }
                } else {
                    Color.clear
                }
            }
            .navigationTitle(summary.map { "\($0.deviceModel)" } ?? "Summary")
            .toolbar { Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") } }
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func maxMs(_ apps: [AppUsage]) -> Int64 { max(1, apps.map { $0.totalMs }.max() ?? 1) }

    private func load() async {
        loading = summary == nil
        error = nil
        do { summary = try await api.summary() }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}

// MARK: - Shared rows

struct AppRow: View {
    let app: AppUsage
    let maxMs: Int64
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(app.label).fontWeight(.medium)
                Spacer()
                Text(Fmt.duration(app.totalMs)).foregroundStyle(.secondary)
            }
            ProgressView(value: Double(app.totalMs), total: Double(maxMs))
                .tint(.green)
            Text("\(app.launches) opens").font(.caption2).foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }
}

struct LocationRow: View {
    let fix: LocationFix
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(String(format: "%.5f, %.5f  ±%.0fm", fix.lat, fix.lon, fix.accuracyM))
                .font(.subheadline)
            Text(Fmt.time(fix.timestamp)).font(.caption).foregroundStyle(.secondary)
            if let url = URL(string: "https://maps.apple.com/?ll=\(fix.lat),\(fix.lon)") {
                Link("Open in Maps", destination: url).font(.caption)
            }
        }
    }
}

struct ErrorView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "wifi.exclamationmark").font(.largeTitle).foregroundStyle(.secondary)
            Text(message).multilineTextAlignment(.center).font(.subheadline).foregroundStyle(.secondary)
            Button("Try again", action: retry).buttonStyle(.bordered)
        }.padding(30)
    }
}
