{{/*
Chart name, overridable with nameOverride.
*/}}
{{- define "plantshop.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Base name of every object: <release>-<chart>, or just <release> if it already contains the chart name.
Installing the chart twice gives two different prefixes, so object names never clash.
*/}}
{{- define "plantshop.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Full name of one component's objects, e.g. "plantshop-catalog-service".
Usage: include "plantshop.componentName" (dict "root" $ "component" "catalog-service")
*/}}
{{- define "plantshop.componentName" -}}
{{- printf "%s-%s" (include "plantshop.fullname" .root) .component | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "plantshop.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Labels that identify a component's pods; used by Deployment selectors and Services.
Usage: include "plantshop.selectorLabels" (dict "root" $ "component" "catalog-service")
*/}}
{{- define "plantshop.selectorLabels" -}}
app.kubernetes.io/name: {{ include "plantshop.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Full label set for object metadata.
Usage: include "plantshop.labels" (dict "root" $ "component" "catalog-service")
*/}}
{{- define "plantshop.labels" -}}
helm.sh/chart: {{ include "plantshop.chart" .root }}
{{ include "plantshop.selectorLabels" . }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: {{ include "plantshop.name" .root }}
{{- end }}

{{/*
Image of a Spring service: <image.repositoryPrefix>/<component>:<tag>.
The service's own image.repository / image.tag win; the tag falls back to image.tag, then to appVersion.
Usage: include "plantshop.image" (dict "root" $ "component" "catalog-service" "values" .Values.catalogService)
*/}}
{{- define "plantshop.image" -}}
{{- $image := .values.image | default dict }}
{{- $repository := $image.repository | default (printf "%s/%s" .root.Values.image.repositoryPrefix .component) }}
{{- $tag := $image.tag | default .root.Values.image.tag | default .root.Chart.AppVersion }}
{{- printf "%s:%s" $repository (toString $tag) }}
{{- end }}

{{/*
Name of the shared ConfigMap / Secret.
*/}}
{{- define "plantshop.configMapName" -}}
{{- include "plantshop.componentName" (dict "root" . "component" "config") }}
{{- end }}

{{/*
Name of the DB Secret. With existingSecret set, the chart renders no Secret and every Deployment
references the Secret created by hand (kubectl create secret ...), so no password lives in Git.
*/}}
{{- define "plantshop.secretName" -}}
{{- .Values.existingSecret | default (include "plantshop.componentName" (dict "root" . "component" "db-secret")) }}
{{- end }}

{{/*
Init container that blocks until every "<component>:<port>" target accepts TCP connections
(Kubernetes has no depends_on). Component names are turned into this release's Service names.
Usage: include "plantshop.waitFor" (dict "root" $ "targets" (list "config-service:8888" "kafka:9092"))
*/}}
{{- define "plantshop.waitFor" -}}
- name: wait-for-dependencies
  image: {{ printf "%s:%s" .root.Values.waitImage.repository (toString .root.Values.waitImage.tag) }}
  imagePullPolicy: IfNotPresent
  command:
    - sh
    - -c
    - |
      for target in {{- range .targets }} {{ include "plantshop.componentName" (dict "root" $.root "component" .) }}{{ end }}; do
        until nc -z -w 2 ${target%:*} ${target#*:}; do
          echo "waiting for $target"; sleep 3
        done
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 100m
      memory: 32Mi
{{- end }}

{{/*
Spring Boot health probes (liveness/readiness groups are enabled automatically on Kubernetes).
The startup probe gives the JVM time to start before the other probes take over.
*/}}
{{- define "plantshop.springProbes" -}}
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  initialDelaySeconds: 15
  periodSeconds: 5
  failureThreshold: {{ .Values.probes.startupFailureThreshold }}
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: http
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
{{- end }}
