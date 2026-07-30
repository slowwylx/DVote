#!/usr/bin/env bash
set -euo pipefail

readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

fail() {
  echo "Repository verification failed: $1" >&2
  exit 1
}

readonly required_files=(
  ".firebaserc.example"
  "app/google-services.json.example"
  "functions/package-lock.json"
  "firestore-tests/package-lock.json"
  "gradle/wrapper/gradle-wrapper.jar"
  "gradle/wrapper/gradle-wrapper.properties"
)
for required_file in "${required_files[@]}"; do
  test -f "$required_file" || fail "missing $required_file"
done

readonly expected_wrapper_jar_sha256="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
if command -v sha256sum >/dev/null 2>&1; then
  actual_wrapper_jar_sha256="$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)"
else
  actual_wrapper_jar_sha256="$(shasum -a 256 gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)"
fi
test "$actual_wrapper_jar_sha256" = "$expected_wrapper_jar_sha256" ||
  fail "Gradle wrapper JAR checksum does not match the reviewed Gradle 9.5.1 wrapper"

grep -Fqx \
  "distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f" \
  gradle/wrapper/gradle-wrapper.properties ||
  fail "Gradle distribution checksum is missing or changed"
grep -Fqx \
  "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip" \
  gradle/wrapper/gradle-wrapper.properties ||
  fail "Gradle distribution URL is not the reviewed 9.5.1 binary"
git ls-files --stage gradlew | grep -Eq '^100755 ' ||
  fail "gradlew must be committed as executable"

while IFS= read -r action_reference; do
  if [[ ! "$action_reference" =~ uses:[[:space:]]+[^@[:space:]]+@[0-9a-f]{40}([[:space:]]+\#.*)?$ ]]; then
    fail "GitHub Action is not pinned to a full commit SHA: $action_reference"
  fi
done < <(grep -Eh '^[[:space:]]*uses:' .github/workflows/*.yml)

readonly forbidden_path_pattern='(^|/)(\.firebaserc|secret\.properties|local\.properties|google-services\.json|\.env(\..*)?|credentials[^/]*\.json|[^/]*(service[-_]?account|firebase-adminsdk)[^/]*\.json|[^/]*\.(jks|keystore|p12|pfx|pem|key|pk8|mobileprovision))$'
readonly generated_path_pattern='(^|/)(build|node_modules)(/|$)|^functions/(lib|lib-test)(/|$)|(^|/)(firebase-export-[^/]*|firestore-debug\.log)(/|$)|\.(apk|aab|apks|class|hprof)$'
readonly secret_content_pattern='AIza[0-9A-Za-z_-]{35}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|glpat-[A-Za-z0-9_-]{20,}|(AKIA|ASIA)[A-Z0-9]{16}|xox[baprs]-[A-Za-z0-9-]{20,}|npm_[A-Za-z0-9]{30,}|(_authToken|NPM_TOKEN)[[:space:]]*=[[:space:]]*[^$[:space:]]'

while IFS= read -r -d '' candidate_file; do
  if [[ "$candidate_file" =~ $forbidden_path_pattern ]] &&
    [[ "$candidate_file" != ".env.example" ]] &&
    [[ "$candidate_file" != */.env.example ]]; then
    fail "secret-bearing path is visible to Git: $candidate_file"
  fi
  if [[ "$candidate_file" =~ $generated_path_pattern ]]; then
    fail "generated artifact is visible to Git: $candidate_file"
  fi

  if git ls-files --error-unmatch -- "$candidate_file" >/dev/null 2>&1; then
    if git show ":$candidate_file" | grep -aE "$secret_content_pattern" >/dev/null; then
      fail "high-confidence secret material found in the Git index for $candidate_file"
    fi
  elif test -f "$candidate_file" &&
    grep -aE "$secret_content_pattern" "$candidate_file" >/dev/null; then
    fail "high-confidence secret material found in untracked file $candidate_file"
  fi
done < <(git ls-files --cached --others --exclude-standard -z)

if [[ "${1:-}" == "--history" ]]; then
  while IFS= read -r commit_sha; do
    if git grep -I -E "$secret_content_pattern" "$commit_sha" >/dev/null; then
      fail "high-confidence secret material found in Git history at $commit_sha"
    fi
  done < <(git rev-list --all)
fi

echo "Repository wrapper, action pins, secret paths, secret content, and generated artifacts are verified."
