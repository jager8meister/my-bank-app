{{- define "notifications-service.fullname" -}}
{{- printf "%s-notifications-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
