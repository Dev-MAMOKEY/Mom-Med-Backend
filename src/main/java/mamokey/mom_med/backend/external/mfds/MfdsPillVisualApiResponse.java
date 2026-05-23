package mamokey.mom_med.backend.external.mfds;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

record MfdsPillVisualApiResponse(@JsonProperty("body") Body body) {

	record Body(@JsonProperty("items") List<MfdsPillVisualItem> items) {
	}
}
