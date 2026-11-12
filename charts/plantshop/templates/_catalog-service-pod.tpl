{{/*
Pod template of catalog-service, shared by the Deployment and the Argo Rollout (Lab 6),
so switching catalogService.rollout.enabled never changes the pods themselves.
Call with the root context: include "plantshop.catalogService.podTemplate" .
*/}}
{{- define "plantshop.catalogService.podTemplate" -}}
{{- $c := .Values.catalogService }}
{{- $component := "catalog-service" -}}
metadata:
  labels:
    {{- include "plantshop.selectorLabels" (dict "root" . "component" $component) | nindent 4 }}
    app.kubernetes.io/part-of: {{ include "plantshop.name" . }}
  annotations:
    # Environment variables are read only at startup: restart the pods when the ConfigMap changes
    checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
spec:
  enableServiceLinks: false
  terminationGracePeriodSeconds: 45
  # Kubernetes has no depends_on: wait until the dependencies accept connections
  initContainers:
    {{- include "plantshop.waitFor" (dict "root" . "targets" (list (printf "config-service:%v" .Values.configService.service.port) (printf "discovery-service:%v" .Values.discoveryService.service.port) "kafka:9092" "catalog-db:5432")) | nindent 4 }}
  containers:
    - name: {{ $component }}
      image: {{ include "plantshop.image" (dict "root" . "component" $component "values" $c) }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      ports:
        - name: http
          containerPort: {{ $c.port }}
      envFrom:
        - configMapRef:
            name: {{ include "plantshop.configMapName" . }}
        - secretRef:
            name: {{ include "plantshop.secretName" . }}
      env:
        - name: DB_HOST
          value: {{ include "plantshop.componentName" (dict "root" . "component" "catalog-db") }}
      resources:
        {{- toYaml ($c.resources | default .Values.resources) | nindent 8 }}
      {{- include "plantshop.springProbes" . | nindent 6 }}
{{- end }}
