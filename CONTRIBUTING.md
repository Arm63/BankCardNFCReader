# Contributing to BankCardNFCReader

Thanks for considering a contribution. Please open an issue first for non-trivial work so we can align on scope before you spend time.

## Development setup

```bash
git clone https://github.com/Arm63/BankCardNFCReader.git
cd BankCardNFCReader
./gradlew :android-bank-card-reader:assemble :android-bank-card-reader:test
```

JDK 17 is required.

## Branching

Branch from `main`:

- `feat/<short-name>` for new features
- `fix/<short-name>` for bug fixes
- `docs/<short-name>` for docs only
- `chore/<short-name>` for build / tooling

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(reader): expose offline PIN tries from tag 9F17
fix(tlv): handle empty 5F20 buffer
docs(readme): correct JitPack URL
```

Keep the subject line under 72 characters. Use the body to explain *why*, not *what*.

## Code style

- Kotlin official style. Run `./gradlew ktlintFormat` if ktlint is configured.
- Public API needs KDoc. Document EMV tag references where relevant (`5F20`, `9F17`, `9F6E`, `9F19`).
- Do not log PAN or any cardholder data. The library is read-only and must not persist card data.

## Tests

- Unit tests live under `android-bank-card-reader/src/test/`.
- Run `./gradlew :android-bank-card-reader:test` before pushing.
- New TLV parsers, AID handlers, or detection logic require unit tests.
- Manual NFC testing with real hardware is required for changes that touch the read path. Note in the PR which devices and card brands you tested.

## DCO

By submitting a contribution you certify that you wrote the code or have the right to submit it under the project's MIT license.

## Pull requests

- One logical change per PR.
- Reference the related issue.
- Update the changelog under the `Unreleased` section in `README.md` if your change is user-visible.
- Wait for CI to pass.

## Reporting security issues

Do not open public issues for security problems. Follow [SECURITY.md](SECURITY.md).
