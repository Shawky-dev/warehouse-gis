import { api } from "@/lib/api";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";

export interface IfcModelSummary {
  id: string;
  originalName: string;
  uploadedAt: string;
}

function basePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}/ifc/models`;
}

export async function listIfcModels(tenantSlug: string): Promise<IfcModelSummary[]> {
  const res = await api.get<IfcModelSummary[]>(basePath(tenantSlug));
  return res.data;
}

export async function uploadIfcModel(tenantSlug: string, file: File): Promise<IfcModelSummary> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await api.post<IfcModelSummary>(basePath(tenantSlug), formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return res.data;
}

export async function fetchIfcModelBuffer(tenantSlug: string, id: string): Promise<Uint8Array> {
  const res = await api.get<ArrayBuffer>(`${basePath(tenantSlug)}/${id}/file`, {
    responseType: "arraybuffer",
  });
  return new Uint8Array(res.data);
}

export async function deleteIfcModel(tenantSlug: string, id: string): Promise<void> {
  await api.delete(`${basePath(tenantSlug)}/${id}`);
}
