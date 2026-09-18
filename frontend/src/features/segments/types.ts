export type SegmentStatus = "ACTIVE" | "RETIRED";
export type SegmentConditionType = "ATTRIBUTE" | "USER_KEY";
export type ConditionOperator = "EQUALS" | "IN";

export interface SegmentCondition {
  type: SegmentConditionType;
  attribute: string | null;
  operator: ConditionOperator;
  values: string[];
}

export interface Segment {
  id: string;
  projectId: string;
  key: string;
  name: string;
  status: SegmentStatus;
  conditions: SegmentCondition[];
  createdBy: string | null;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
}

export interface SegmentConditionRequest {
  type: SegmentConditionType;
  attribute?: string;
  operator: ConditionOperator;
  values: string[];
}

export interface CreateSegmentRequest {
  key: string;
  name: string;
  conditions: SegmentConditionRequest[];
}

export interface UpdateSegmentRequest {
  name: string;
  conditions: SegmentConditionRequest[];
}
