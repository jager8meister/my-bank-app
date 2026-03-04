{{- define "keycloak.fullname" -}}
{{- printf "%s-keycloak" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
