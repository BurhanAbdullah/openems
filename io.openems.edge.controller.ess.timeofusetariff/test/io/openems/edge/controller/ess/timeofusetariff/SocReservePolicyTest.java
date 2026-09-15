package io.openems.edge.controller.ess.timeofusetariff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.openems.edge.energy.api.simulation.GlobalOptimizationContext;
import io.openems.edge.energy.api.simulation.periods.PeriodData;

class SocReservePolicyTest {

	@Test
	void shouldReturnFloorWithoutForecastDeficit() {
		final var goc = context(10_000, List.of(period(LocalDateTime.of(2026, 9, 15, 12, 0), 1000, 1000)));

		assertEquals(20, SocReservePolicy.calculateReserveSoc(goc, 0.20, 0.80, 1.0));
	}

	@Test
	void shouldIncreaseReserveWithForecastDeficit() {
		final var goc = context(10_000, List.of(period(LocalDateTime.of(2026, 9, 15, 12, 0), 0, 5000)));

		// A one-hour 5 kW deficit is 0.5 SoC; with weight 0.5, reserve becomes 45%.
		assertEquals(45, SocReservePolicy.calculateReserveSoc(goc, 0.20, 0.80, 0.50));
	}

	@Test
	void shouldCapReserveAtCeiling() {
		final var goc = context(10_000, List.of(period(LocalDateTime.of(2026, 9, 15, 12, 0), 0, 50_000)));

		assertEquals(80, SocReservePolicy.calculateReserveSoc(goc, 0.20, 0.80, 1.0));
	}

	@Test
	void shouldRejectInvalidParameters() {
		final var goc = context(10_000, List.of());

		assertThrows(IllegalArgumentException.class,
				() -> SocReservePolicy.calculateReserveSoc(goc, 0.9, 0.8, 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> SocReservePolicy.calculateReserveSoc(goc, 0.2, 0.8, -0.1));
	}

	private static GlobalOptimizationContext context(int capacity, List<GlobalOptimizationContext.Period> periods) {
		return new GlobalOptimizationContext(//
				periods,
				new GlobalOptimizationContext.Ess(capacity, 0, 0, 0),
				null,
				null,
				null,
				null);
	}

	private static GlobalOptimizationContext.Period period(LocalDateTime time, int production, int consumption) {
		final var data = PeriodData.builder()//
				.withProduction(production)//
				.withConsumption(new PeriodData.Prediction(consumption, consumption))//
				.build();
		return new GlobalOptimizationContext.Period.Quarter(//
					0, time.atZone(ZoneId.of("UTC")), null, data);
	}
}
