package com.studentsharding.dto;

import java.util.List;

public record Distribution(long total, List<NodeCount> nodes) {
}
