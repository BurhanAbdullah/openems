package io.openems.edge.controller.ess.timeofusetariff;

import java.util.Objects;

import io.openems.edge.energy.api.simulation.GlobalOptimizationContext;

/**
 * Computes a conservative state-of-charge reserve for tariff optimization.
 *
 * <p>The reserve increases with the forecasted energy deficit and is bounded by
 * configurable minimum/maximum SoC limits. This keeps the tariff optimizer from
 * scheduling grid charging solely for price minimization when the forecast
 * indicates a later energy shortfall.
 */
final class SocReservePolicy {

	private SocReservePolicy() {
	}

	static int calculateReserveSoc(GlobalOptimizationContext goc, double reserveFloor, double reserveCeiling,
			double deficitWeight) {
		validate(reserveFloor, reserveCeiling, deficitWeight);

		final double capacity = goc.ess().totalEnergy();
		if (capacity <= 0) {
			return (int) Math.round(reserveFloor * 100.0);
		}

		double forecastDeficit = 0.0;
		for (var period : goc.periods()) {
			final var data = period.data();
			if (data.consumption().isEmpty()) {
				continue;
			}
			final double netDeficit = data.consumption().orElseThrow().actual() - data.production();
			if (netDeficit > 0) {
				forecastDeficit += period.duration().convertPowerToEnergy((int) Math.ceil(netDeficit));
			}
		}

		final double deficitSoc = forecastDeficit / capacity;
		final double reserveSoc = clamp(reserveFloor + deficitWeight * deficitSoc, reserveFloor, reserveCeiling);
		return (int) Math.round(reserveSoc * 100.0);
	}

	private static void validate(double reserveFloor, double reserveCeiling, double deficitWeight) {
		if (!Double.isFinite(reserveFloor) || !Double.isFinite(reserveCeiling) || !Double.isFinite(deficitWeight)) {
			throw new IllegalArgumentException("Reserve policy parameters must be finite");
		}
		if (reserveFloor < 0.0 || reserveFloor > 1.0) {
			throw new IllegalArgumentException("reserveFloor must be between 0.0 and 1.0");
		}
		if (reserveCeiling < reserveFloor || reserveCeiling > 1.0) {
			throw new IllegalArgumentException("reserveCeiling must be between reserveFloor and 1.0");
		}
		if (deficitWeight < 0.0) {
			throw new IllegalArgumentException("deficitWeight must be non-negative");
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
