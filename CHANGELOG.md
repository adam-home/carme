# Changelog

## v0.1.4 (2022-01-22)
New configuration options `:privkey-file` and `:cert-file` added to allow loading of PEM files to provide SSL capability, as an alternative to the existing Java keystore support.

Fixed a bug where the client's socket wasn't shut down after writing; this was causing clients such as Bombadillo and Amfora to fail.

Exit cleanly if the configuration can't be found or parsed, rather than crashing.
