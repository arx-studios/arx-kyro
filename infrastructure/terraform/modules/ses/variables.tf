variable "enable_ses" {
  description = "Create the SES domain identity. Off by default: SES is enabled only when real sending is approved."
  type        = bool
  default     = false
}

variable "domain" {
  description = "Sending domain (only used when enable_ses is true)."
  type        = string
  default     = ""
}
