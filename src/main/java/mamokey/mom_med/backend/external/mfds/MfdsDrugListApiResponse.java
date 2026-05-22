package mamokey.mom_med.backend.external.mfds;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

record MfdsDrugListApiResponse(@JsonProperty("body") Body body) {

	record Body(
			@JsonProperty("totalCount") Integer totalCount,
			@JsonProperty("items") List<MfdsDrugListItem> items
	) {
	}
}
