package com.eczam.medications.seed;

import com.eczam.medications.Gtin;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a small set of well-known Turkish medicines so a fresh database is
 * immediately demoable — barcode scan, inventory, schedules and the catalog all
 * work — without importing the full 20k-row dataset (which needs {@code ilac.json}
 * and the Stage A/B pipeline). Enable with {@code SEED_SAMPLE=true} (or
 * {@code eczam.seed.sample=true}); a no-op when the catalog already has rows, so
 * it never clashes with the real seed.
 */
@Component
@Order(50)
@ConditionalOnProperty(name = "eczam.seed.sample", havingValue = "true")
public class SampleCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleCatalogSeeder.class);

    private final MedicationRepository medications;

    public SampleCatalogSeeder(MedicationRepository medications) {
        this.medications = medications;
    }

    private record Sample(String name, String generic, String manufacturer,
                          String barcode, String atc, String form, String strength) {}

    /** A handful of common Turkish OTC/Rx products with real EAN-13 barcodes. */
    private static final List<Sample> SAMPLES = List.of(
            new Sample("PAROL 500 mg 20 Tablet", "Parasetamol", "Atabay",
                    "8699546011027", "N02BE01", "tablet", "500 mg"),
            new Sample("ARVELES 25 mg 20 Film Tablet", "Deksketoprofen", "Menarini",
                    "8699809570016", "M01AE17", "tablet", "25 mg"),
            new Sample("AUGMENTIN BID 1000 mg 14 Tablet", "Amoksisilin + Klavulanik Asit", "GSK",
                    "8699522090019", "J01CR02", "tablet", "1000 mg"),
            new Sample("NUROFEN 400 mg 20 Tablet", "İbuprofen", "Reckitt",
                    "8699504090024", "M01AE01", "tablet", "400 mg"),
            new Sample("CORACTEN 20 mg 30 Kapsül", "Nifedipin", "Actavis",
                    "8699517150012", "C08CA05", "capsule", "20 mg"),
            new Sample("GLİFOR 1000 mg 100 Tablet", "Metformin", "Bilim",
                    "8699569090014", "A10BA02", "tablet", "1000 mg"),
            new Sample("VENTOLIN İnhaler 100 mcg", "Salbutamol", "GSK",
                    "8699522570013", "R03AC02", "inhaler", "100 mcg"),
            new Sample("DEVİT-3 Damla 50000 IU", "Kolekalsiferol", "Deva",
                    "8699525350015", "A11CC05", "drops", "50000 IU"));

    @Override
    public void run(ApplicationArguments args) {
        long existing = medications.count();
        if (existing > 0) {
            log.info("Sample catalog seed skipped — {} medications already present", existing);
            return;
        }
        int n = 0;
        for (Sample s : SAMPLES) {
            Medication m = new Medication();
            m.setName(s.name());
            m.setGenericName(s.generic());
            m.setManufacturer(s.manufacturer());
            m.setBarcode(s.barcode());
            Gtin.canonicalize(s.barcode()).ifPresent(m::setGtin);
            m.setAtcCode(s.atc());
            m.setAtcGroup(SourceText.atcGroup(s.atc()));
            m.setForm(s.form());
            m.setStrength(s.strength());
            medications.save(m);
            n++;
        }
        log.info("Sample catalog seeded: {} medications (set SEED_SAMPLE=false to disable)", n);
    }
}
