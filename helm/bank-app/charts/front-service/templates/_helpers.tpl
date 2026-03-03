{{- define "front-service.fullname" -}}
{{- printf "%s-front-service" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
