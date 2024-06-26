{{/* vim: set filetype=mustache: */}}
{{/*

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "interop-proxy-server.fullname" -}}
{{- printf "%s" .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Environmental variables */}}
{{- define "interop-proxy-server.env" -}}
- name: DD_AGENT_HOST
  valueFrom:
    fieldRef:
      fieldPath: status.hostIP
- name: DD_SERVICE
  valueFrom:
    configMapKeyRef:
      name: interop-proxy-server-config
      key: DD_SERVICE
- name: DD_ENV
  valueFrom:
    configMapKeyRef:
      name: interop-proxy-server-config
      key: K8S_ENV
- name: K8S_ENV
  valueFrom:
    configMapKeyRef:
      name: interop-proxy-server-config
      key: K8S_ENV
      key: DATABASE_PASSWORD
{{- end -}}
