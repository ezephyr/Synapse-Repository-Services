package org.sagebionetworks.bridge.model.data.units;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A unit of measurement. Each unit of measure can specify a normalized unit of 
 * measure that it can convert to and from, using a specific conversion <em>factor</em>. 
 * (If this value is left null, it converts to itself because it is already normalized).
 * 
 */
public enum Units {
	
	LITER(1.0, "liter", "L"),
	DECILITER(LITER,  0.1, "deciliter", "dL"), // 10^-1 L
	CENTILITER(LITER, 0.01, "centiliter", "cL"), // 10^-2 L
	MILLILITER(LITER, 0.001, "millilter", "mL"), // 10^-3 L
	MICROLITER(LITER, 0.0001, "microliter", "µL", "mcL", "uL"), // 10^-6 L
	NANOLITER(LITER,  0.00001, "nanoliter", "nL"), // 10^-9 L
	PICOLITER(LITER,  0.000001, "picoliter", "pL"), // 10^-12 L
	FEMTOLITER(LITER, 0.0000001, "femtoliter", "fL"), // 10^-15

	GRAM(1.0, "gram", "g"),
	DECIGRAM(GRAM,  0.1, "decigram", "dg"), // 10^-1 g
	CENTIGRAM(GRAM, 0.01, "centigram", "cg"), // 10^-2 g
	MILLIGRAM(GRAM, 0.001, "milligram", "mg"), // 10^-3 g
	MICROGRAM(GRAM, 0.0001, "microgram", "mcg"), // 10^-6 g
	NANOGRAM(GRAM,  0.00001, "nanogram", "ng"), // 10^-9 g
	PICOGRAM(GRAM,  0.000001, "picogram", "pg"), // 10^-12 g
	FEMTOGRAM(GRAM, 0.0000001, "femtogram", "fg"), // 10^-15 g
	
	MOLE(1.0, "mole", "mol"),
	DECIMOLE(MOLE,  0.1, "decimole", "dmol"), // 10^-1 mole
	CENTIMOLE(MOLE, 0.01, "centimole", "cmol"), // 10^-2 mole
	MILLIMOLE(MOLE, 0.001, "millimole", "mmol"), // 10^-3 mole
	MICROMOLE(MOLE, 0.0001, "micromole", "µmol", "mcmol", "umol"), // 10^-6 mole
	NANOMOLE(MOLE,  0.00001, "nanomole", "nmol"), // 10^-9 mole
	PICOMOLE(MOLE,  0.000001,"picomole", "pmol"), // 10^-12 mole
	FEMTOMOLE(MOLE, 0.0000001, "femtomole", "fmol"), // 10^-15 mole
	
	CUBIC_METER(1.0, "cubic meter", "cu m"),
	CUBIC_DECIMETER(CUBIC_METER,  0.1, "cubic decimeter", "cu dm"), // 10^-1 m
	CUBIC_CENTIMETER(CUBIC_METER, 0.01, "cubic centimeter", "cu cm"), // 10^-2 m
	CUBIC_MILLIMETER(CUBIC_METER, 0.001, "cubic millimeter", "cu mm"), // 10^-3 m
	CUBIC_MICROMETER(CUBIC_METER, 0.0001, "cubic micrometer", "cu mc"),	// 10^-6 m
	CUBIC_NANOMETER(CUBIC_METER,  0.00001, "cubic nanometer", "cu nm"), // 10^-9 m
	CUBIC_PICOMETER(CUBIC_METER,  0.000001, "cubic picometer", "cu pm"), // 10^-12 m
	CUBIC_FEMTOMETER(CUBIC_METER, 0.0000001, "cubic femtometer", "cu fm"), // 10^-15 m

	// Ratios that are equivalent, but expressed for different volumes. In conversion 
	// the number will not change, only unit of measurement.
	THOUSANDS_PER_MICROLITER(1.0, "thousands per microliter", "K/µL", "K/mcL", "K/uL"),
	BILLIONS_PER_LITER(THOUSANDS_PER_MICROLITER, 1.0, "billions per liter", "10^9/L", "10e9/L", "9/L"),

	MILLIONS_PER_MICROLITER(1.0, "millions per microliter", "M/µL", "M/mcL", "M/uL"),
	MILLIONS_PER_CUBIC_MILLIMETER(MILLIONS_PER_MICROLITER, 1.0, "millions per cubic millimeter", "M/cu mm"),
	TRILLIONS_PER_LITER(MILLIONS_PER_MICROLITER, 1.0, "trillions per liter", "10^12/L", "10e12/L", "12/L"), 
	
	PERCENTAGE(1.0, "percentage", "%"),
	GRAMS_PER_LITER(1.0, "grams per liter", "g/L"),
	
	MILLIMOLES_PER_LITER(1.0, "millimoles per liter", "mmol/L"),
	
	MILLIGRAMS_PER_LITER(1.0, "milligrams per liter", "mg/L"),
	MILLIGRAMS_PER_DECILITER(10, "milligrams per deciliter", "mg/dL"),
	
	// This conversion appears to be wrong, phosphorus' molecular weight is 30.9737622, 
	// the number of grams in one mole. 
	PHOSPHORUS_MG_DL(MILLIMOLES_PER_LITER, 0.3229, "phosphorus (mg/dL)", "phosphorus (mg/dL)"),
	// what I think this should be:
	// PHOSPHORUS_MG_DL(MILLIMOLES_PER_LITER, 0.30974, "phosphorus (mg/dL)", "phosphorus (mg/dL)"),
	
	// This % can mean two different things depending on the notation, CV or SD. The unit 
	// must be exported with a data set, or the number will lack meaning.
	PERCENTAGE_CV_OR_SD(1.0, "standard or coefficient variation", "% CV", "% SD");
	
	private final Units normalizedUnit;
	private final String name;
	private final List<String> labels;
	private final BigDecimal factor;
	
	public static Units unitFromString(String string) {
		if (string != null) {
			for (Units unit : values()) {
				if (unit.name.equals(string)) {
					return unit;
				}
				for (String label : unit.labels) {
					if (string.equals(label)) {
						return unit;
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * Assumes the amount is in the normalized unit for this unit. 
	 * Converts to this unit.
	 * 
	 * @param amount
	 * @return
	 */
	public Measure convertFromNormalized(double amount) {
		if (normalizedUnit == null) {
			return new Measure(amount, this);
		}
		return new Measure(BigDecimal.valueOf(amount).divide(factor), this);
	}

	/**
	 * Assumes the amount is in this unit. Converts to the normalized
	 * unit for this unit.
	 */
	public Measure convertToNormalized(double amount) {
		if (normalizedUnit == null) {
			return new Measure(amount, this);
		}
		return new Measure(BigDecimal.valueOf(amount).multiply(factor), normalizedUnit);
	}
	
	private Units(Units normalizedUnit, double factor, String name, String... labels) {
		this.normalizedUnit = normalizedUnit;
		this.name = name;
		this.factor = BigDecimal.valueOf(factor);
		this.labels = Collections.unmodifiableList(Arrays.asList(labels));
	}

	private Units(double factor, String name, String... labels) {
		this(null, factor, name, labels);
	}
	
	public List<String> getLabels() {
		return labels;
	}
	
	public String getName() {
		return name;
	}

	public double getFactor() {
		return factor.doubleValue();
	}
	
	public Units getNormalizedUnit() {
		return normalizedUnit;
	}

	BigDecimal getBigDecimalFactor() {
		return factor;
	}
}
