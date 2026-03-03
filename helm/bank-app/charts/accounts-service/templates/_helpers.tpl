{{- define "accounts-service.fullname" -}}
{{- printf "%s-accounts-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
