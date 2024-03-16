with (import <nixpkgs> {});
mkShell {
  buildInputs = [
    clojure
		jdk
    google-cloud-sdk
  ];
}
