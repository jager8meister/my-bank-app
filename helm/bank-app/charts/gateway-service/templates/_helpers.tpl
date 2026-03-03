{{- define "gateway-service.fullname" -}}
{{- printf "%s-gateway-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
