import SwiftUI
import MapKit

struct LocationView: View {
    @EnvironmentObject var api: ApiClient
    @State private var fixes: [LocationFix] = []
    @State private var error: String?
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Group {
                if let error, !loaded {
                    ErrorView(message: error) { Task { await load() } }
                } else if fixes.isEmpty && loaded {
                    ContentUnavailableView("No location yet",
                        systemImage: "location.slash",
                        description: Text("Make sure location permission is granted on the child's phone."))
                } else {
                    VStack(spacing: 0) {
                        if let latest = fixes.first {
                            Map(initialPosition: .region(region(latest))) {
                                ForEach(fixes.prefix(30)) { f in
                                    Marker(Fmt.time(f.timestamp),
                                           coordinate: CLLocationCoordinate2D(latitude: f.lat, longitude: f.lon))
                                }
                            }
                            .frame(height: 260)
                        }
                        List(fixes) { f in LocationRow(fix: f) }
                    }
                }
            }
            .navigationTitle("Location (3 days)")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func region(_ f: LocationFix) -> MKCoordinateRegion {
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: f.lat, longitude: f.lon),
            span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
        )
    }

    private func load() async {
        error = nil
        do { fixes = try await api.locations(days: 3).locations }
        catch { self.error = error.localizedDescription }
        loaded = true
    }
}
