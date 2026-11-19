import SwiftUI

@main
struct FamilyGuardParentApp: App {
    @StateObject private var api = ApiClient()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(api)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var api: ApiClient

    var body: some View {
        if api.isSignedIn {
            DashboardView()
        } else {
            LoginView()
        }
    }
}
