package org.uet.dse.neo4j.scripting;

import java.io.*;
import java.util.*;

public class MedicalDatasetGenerator {

  static Random r = new Random();

  static int HOSPITALS = 3;
  static int DEPARTMENTS = 5;
  static int DOCTORS = 5;
  static int NURSES = 5;
  static int PATIENTS = 15;
  static int MEDICATIONS = 30;
  static int DISEASES = 25;

  public static void main(String[] args) throws Exception {

    BufferedWriter w = new BufferedWriter(new FileWriter("MedicalDataset_BIG.txt"));

    createHospitals(w);
    createDepartments(w);
    createDoctors(w);
    createNurses(w);
    createPatients(w);
    createMedications(w);
    createDiseases(w);
    createMedicalRecords(w);
    createDosageAndPlan(w);
    createHospitalizations(w);
    createPrescriptions(w);
    createAppointments(w);
    createConsultations(w);

    w.write("\ncheck\n");
    w.close();

    System.out.println("BIG DATASET GENERATED ✅");
  }

  // ================= HOSPITAL =================
  static void createHospitals(BufferedWriter w) throws Exception {
    for (int i = 1; i <= HOSPITALS; i++) {
      w.write("!create h" + i + " : Hospital\n");
      w.write("!set h" + i + ".name := 'Hospital" + i + "'\n");
      w.write("!set h" + i + ".totalBudget := 10000000\n");
      w.write("!set h" + i + ".location := 'City'\n");
      w.write("!set h" + i + ".totalBeds := 1000\n");
      w.write("!set h" + i + ".availableICUs := 100\n");
      w.write("!set h" + i + ".rating := 4.5\n");
      w.write("!set h" + i + ".isPublic := true\n");
      w.write("!set h" + i + ".emergencyContact := '1900'\n\n");
    }
  }

  // ================= DEPARTMENT + COMPOSITION =================
  static void createDepartments(BufferedWriter w) throws Exception {
    for (int i = 1; i <= DEPARTMENTS; i++) {
      w.write("!create d" + i + " : Department\n");
      w.write("!set d" + i + ".deptName := 'Dept" + i + "'\n");
      int h = r.nextInt(HOSPITALS) + 1;
      w.write("!insert (h" + h + ",d" + i + ") into HospitalStructure\n\n");
    }
  }

  // ================= DOCTOR =================
  static void createDoctors(BufferedWriter w) throws Exception {
    for (int i = 1; i <= DOCTORS; i++) {
      w.write("!create doc" + i + " : Doctor\n");
      w.write("!set doc" + i + ".name := 'Doctor" + i + "'\n");
      w.write("!set doc" + i + ".age := " + (30 + r.nextInt(30)) + "\n");
      w.write("!set doc" + i + ".gender := Gender::MALE\n");
      w.write("!set doc" + i + ".doctorID := 'D" + i + "'\n");
      w.write("!set doc" + i + ".specialty := Specialty::CARDIOLOGY\n");
      w.write("!set doc" + i + ".consultationFee := 500\n");
      w.write("!set doc" + i + ".yearsOfExperience := 10\n");
      w.write("!set doc" + i + ".qualification := 'MD'\n");
      w.write("!set doc" + i + ".isAvailable := true\n");
      w.write("!set doc" + i + ".shiftSchedule := Sequence{Set{1,2},Set{3}}\n\n");
    }
  }

  // ================= NURSE =================
  static void createNurses(BufferedWriter w) throws Exception {
    for (int i = 1; i <= NURSES; i++) {
      w.write("!create nurse" + i + " : Nurse\n");
      w.write("!set nurse" + i + ".name := 'Nurse" + i + "'\n");
      w.write("!set nurse" + i + ".age := 28\n");
      w.write("!set nurse" + i + ".gender := Gender::FEMALE\n");
      w.write("!set nurse" + i + ".nurseID := 'N" + i + "'\n");
      w.write("!set nurse" + i + ".isHeadNurse := false\n");
      w.write("!set nurse" + i + ".certificationLevel := 2\n");
      w.write("!set nurse" + i + ".assignedWard := 'WardA'\n");
      w.write("!set nurse" + i + ".onCall := false\n");
      w.write("!set nurse" + i + ".hourlyRate := 20\n");
      w.write("!set nurse" + i + ".lastTrainingDate := '2025-01-01'\n\n");
    }
  }

  // ================= PATIENT =================
  static void createPatients(BufferedWriter w) throws Exception {
    for (int i = 1; i <= PATIENTS; i++) {
      w.write("!create p" + i + " : Patient\n");
      w.write("!set p" + i + ".name := 'Patient" + i + "'\n");
      w.write("!set p" + i + ".age := " + (1 + r.nextInt(90)) + "\n");
      w.write("!set p" + i + ".gender := Gender::MALE\n");
      w.write("!set p" + i + ".patientID := 'P" + i + "'\n");
      w.write("!set p" + i + ".isEmergency := false\n");
      w.write("!set p" + i + ".bloodType := BloodType::A_POS\n\n");
    }
  }

  // ================= MEDICAL RECORD + COMPOSITION =================
  static void createMedicalRecords(BufferedWriter w) throws Exception {
    int recordCounter = 1;
    for (int i = 1; i <= PATIENTS; i++) {
      int records = r.nextInt(3) + 1; // <=7 safe
      for (int j = 0; j < records; j++) {
        w.write("!create mr" + recordCounter + " : MedicalRecord\n");
        w.write("!insert (p" + i + ",mr" + recordCounter + ") into PatientRecord\n");
        for (int k = 1; k <= 5; k++) {
          w.write("!create re" + recordCounter + "_" + k + " : RecordEntry\n");
          w.write("!set re" + recordCounter + "_" + k + ".entryDate := '2026-01-01'\n");
          w.write("!set re" + recordCounter + "_" + k + ".description := 'Checkup'\n");
          w.write("!insert (mr" + recordCounter + ",re" + recordCounter + "_" + k + ") into RecordEntries\n");
        }
        recordCounter++;
      }
    }
  }

  // ================= MEDICATION =================
  static void createMedications(BufferedWriter w) throws Exception {
    for (int i = 1; i <= MEDICATIONS; i++) {
      w.write("!create m" + i + " : Medication\n");
      w.write("!set m" + i + ".brandName := 'Brand" + i + "'\n");
      w.write("!set m" + i + ".genericName := 'Gen" + i + "'\n");
      w.write("!set m" + i + ".price := 10\n\n");
    }
  }

  // ================= DISEASE =================
  static void createDiseases(BufferedWriter w) throws Exception {
    for (int i = 1; i <= DISEASES; i++) {
      w.write("!create dis" + i + " : Disease\n");
      w.write("!set dis" + i + ".diseaseName := 'Disease" + i + "'\n\n");
    }
  }

  // ================= DOSAGE =================
  static void createDosageAndPlan(BufferedWriter w) throws Exception {
    for (int i = 1; i <= 2000; i++) {
      w.write("!create ds" + i + " : Dosage\n");
      w.write("!set ds" + i + ".patientNumber := " + i + "\n");
      w.write("!set ds" + i + ".adminMethod := Administration::ENTERAL\n");
      w.write("!set ds" + i + ".brandName := Brand::ASCRIPTIN\n");
    }
    for (int i = 1; i <= 1000; i++) {
      w.write("!create dp" + i + " : DosagePlan\n");
    }
  }

  // ================= ASSOCIATION CLASS =================
  static void createHospitalizations(BufferedWriter w) throws Exception {
    for (int i = 1; i <= 6000; i++) {
      int p = r.nextInt(PATIENTS) + 1;
      int d = r.nextInt(DEPARTMENTS) + 1;
      w.write("!create hos" + i + " : Hospitalization between (p" + p + ",d" + d + ")\n");
      w.write("!set hos" + i + ".admissionDate := '2026-01-01'\n");
      w.write("!set hos" + i + ".roomNumber := 100\n\n");
    }
  }

  static void createPrescriptions(BufferedWriter w) throws Exception {
    for (int i = 1; i <= 8000; i++) {
      int doc = r.nextInt(DOCTORS) + 1;
      int p = r.nextInt(PATIENTS) + 1;
      w.write("!create pr" + i + " : Prescription between (doc" + doc + ",p" + p + ")\n");
      w.write("!set pr" + i + ".prescriptionID := 'RX" + i + "'\n");
      w.write("!set pr" + i + ".dosageInstructions := 'Daily'\n\n");
    }
  }

  static void createAppointments(BufferedWriter w) throws Exception {
    for (int i = 1; i <= 7000; i++) {
      int doc = r.nextInt(DOCTORS) + 1;
      int p = r.nextInt(PATIENTS) + 1;
      int dep = r.nextInt(DEPARTMENTS) + 1;
      w.write("!insert (doc" + doc + ",p" + p + ",d" + dep + ") into Appointment\n");
    }
  }

  static void createConsultations(BufferedWriter w) throws Exception {
    for (int i = 1; i <= 6000; i++) {
      int doc = r.nextInt(DOCTORS) + 1;
      int p = r.nextInt(PATIENTS) + 1;
      int dis = r.nextInt(DISEASES) + 1;
      w.write("!insert (doc" + doc + ",p" + p + ",dis" + dis + ") into Consultation\n");
    }
  }

}
