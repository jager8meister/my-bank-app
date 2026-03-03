{{- define "cash-service.fullname" -}}
{{- printf "%s-cash-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
