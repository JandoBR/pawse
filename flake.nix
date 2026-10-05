{
  description = "Pawse: a native Android app";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        # Where this app's release signing key lives: ~/.config/<app>/keystore.env
        # (see docs/android-signing.md in Dev-kit).
        app = "pawse";

        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        # When a build names a missing build-tools or platform version, add
        # that exact version here: nothing can be downloaded any other way.
        # No NDK or CMake: there is no native code and no Flutter plugin.
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "36.0.0" ];
          platformVersions = [ "36" ];

          includeNDK = false;
          includeSystemImages = false;
          includeEmulator = false;
          useGoogleAPIs = false;
        };

        androidSdk = androidComposition.androidsdk;
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            pkgs.jdk17
            androidSdk
          ];

          shellHook = ''
            export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
            export JAVA_HOME="${pkgs.jdk17}"

            # The release signing key, where there is one.
            if [ -f "$HOME/.config/${app}/keystore.env" ]; then
              . "$HOME/.config/${app}/keystore.env"
            fi
          '';
        };
      }
    );
}
