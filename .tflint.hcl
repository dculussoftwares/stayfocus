# tflint config shared by the pre-commit hook (scripts/dev/hooks/terraform.sh) and CI (security.yml, job iac-tflint).
# Run `tflint --init --config .tflint.hcl` once to download the plugin.
plugin "google" {
  enabled = true
  version = "0.40.0"
  source  = "github.com/terraform-linters/tflint-ruleset-google"
}
