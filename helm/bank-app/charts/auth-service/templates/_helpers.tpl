{{- define "auth-service.fullname" -}}
{{- printf "%s-auth-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
