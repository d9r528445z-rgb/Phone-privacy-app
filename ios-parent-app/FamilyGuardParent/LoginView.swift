import SwiftUI

struct LoginView: View {
    @EnvironmentObject var api: ApiClient
    @State private var hostInput: String = UserDefaults.standard.string(forKey: "fg_host") ?? ""
    @State private var pin: String = ""
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Connect to your child's phone")
                            .font(.title2).bold()
                        Text("Both phones must be on the same Wi-Fi. Enter the address shown on the Family Guard app on the child's phone, then your PIN.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Phone address").font(.caption).foregroundStyle(.secondary)
                        TextField("192.168.1.20:8766", text: $hostInput)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .padding(12)
                            .background(.quaternary, in: RoundedRectangle(cornerRadius: 10))
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Parent PIN").font(.caption).foregroundStyle(.secondary)
                        SecureField("PIN", text: $pin)
                            .keyboardType(.numberPad)
                            .padding(12)
                            .background(.quaternary, in: RoundedRectangle(cornerRadius: 10))
                    }

                    if let error {
                        Text(error).font(.footnote).foregroundStyle(.red)
                    }

                    Button {
                        Task { await signIn() }
                    } label: {
                        HStack {
                            if loading { ProgressView().tint(.white) }
                            Text("Unlock").bold()
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(loading)

                    Text("All data is pulled directly from the child's phone over your local network. Nothing is stored in the cloud.")
                        .font(.caption2).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 8)
                }
                .padding(20)
            }
            .navigationTitle("Family Guard")
        }
    }

    private func signIn() async {
        error = nil
        guard pin.count >= 4 else { error = "PIN must be at least 4 digits."; return }
        loading = true
        defer { loading = false }
        do {
            try await api.signIn(hostInput: hostInput, pin: pin)
        } catch {
            self.error = error.localizedDescription
        }
    }
}
