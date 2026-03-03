{{- define "transfer-service.fullname" -}}
{{- printf "%s-transfer-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
