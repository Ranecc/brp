{{/* Fullname, clipped like every other chart in Christendom. */}}
{{- define "brp.fullname" -}}
{{- printf "%s" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "brp.dbSecret" -}}
{{- if .Values.db.existingSecret -}}
{{- .Values.db.existingSecret -}}
{{- else -}}
{{- printf "%s-db" (include "brp.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "brp.dbUrl" -}}
{{- if .Values.db.url -}}
{{- .Values.db.url -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s-postgres.%s.svc.cluster.local:5432/%s" (include "brp.fullname" .) .Release.Namespace .Values.db.name -}}
{{- end -}}
{{- end -}}
