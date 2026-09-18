import { api } from "@/api/client";
import type { CreateSegmentRequest, Segment, UpdateSegmentRequest } from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listSegments(projectKey: string): Promise<Segment[]> {
  const response = await api
    .get(`projects/${projectKey}/segments`)
    .json<ApiResponse<Segment[]>>();

  return response.data;
}

export async function createSegment(
  projectKey: string,
  request: CreateSegmentRequest,
): Promise<Segment> {
  const response = await api
    .post(`projects/${projectKey}/segments`, { json: request })
    .json<ApiResponse<Segment>>();

  return response.data;
}

export async function updateSegment(
  projectKey: string,
  segmentKey: string,
  request: UpdateSegmentRequest,
): Promise<Segment> {
  const response = await api
    .patch(`projects/${projectKey}/segments/${segmentKey}`, { json: request })
    .json<ApiResponse<Segment>>();

  return response.data;
}

export async function retireSegment(projectKey: string, segmentKey: string): Promise<Segment> {
  const response = await api
    .post(`projects/${projectKey}/segments/${segmentKey}/retire`)
    .json<ApiResponse<Segment>>();

  return response.data;
}
