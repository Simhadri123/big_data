import csv
from collections import defaultdict

# ---------------- Config ----------------
LETTER_TO_POINTS = {
    'EX': 10, 'S': 10,
    'A': 9, 'B': 8,
    'C': 7, 'D': 6,
    'F': 0, 'E': 0,
    'P': 4
}

# ---------------- Load CSV helpers ----------------
def load_csv_dict(path, key_col):
    d = {}
    with open(path, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            d[row[key_col]] = row
    return d

def load_csv_list(path):
    with open(path, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        return list(reader)

# ---------------- Inputs ----------------
student_id_input = input("Enter student_id (or 'ALL' for all students): ").strip()

registrations = load_csv_list("Registrations.csv")
subjects = load_csv_list("SubjectInfo.csv")
grades = load_csv_list("StudentGradesNew.csv")

# ---------------- Build lookups ----------------
# Student -> list of registration rows
students_regs = defaultdict(list)
for r in registrations:
    students_regs[r['student_id']].append(r)

# Subject credits lookup
sub_credits = {}
for s in subjects:
    key = f"{s['RegEventId']}|{s['SubId']}"
    sub_credits[key] = float(s.get('Credits', 0))

# ---------------- Process ----------------
output_rows = []

student_ids = [student_id_input] if student_id_input != "ALL" else students_regs.keys()

for sid in student_ids:
    regs_for_student = {r['id']: r for r in students_regs.get(sid, [])}
    sem_data = defaultdict(list)

    for g in grades:
        reg_id = g['RegId']
        reg_row = regs_for_student.get(reg_id)
        if not reg_row:
            continue

        reg_event = reg_row['RegEventId']
        sub_id = reg_row['sub_id']
        credits = sub_credits.get(f"{reg_event}|{sub_id}", 0)
        grade_letter = g.get('NGrade') or g.get('AttGrade', '')
        grade_letter = grade_letter.strip().upper()
        points = LETTER_TO_POINTS.get(grade_letter, 0)

        sem_data[reg_event].append((credits, points))

    # If student has no sem_data (no grades), add one empty semester entry
    if not sem_data:
        output_rows.append({
            'student_id': sid,
            'RegEventId': '',
            'semester_credits': 0,
            'SGPA': 0,
            'CGPA_to_date': 0
        })
        continue

    total_weighted_all = 0
    total_credits_all = 0

    for reg_event in sorted(sem_data.keys(), key=lambda x: int(x)):
        weighted_sum = sum(c*gp for c, gp in sem_data[reg_event])
        sem_credits = sum(c for c, gp in sem_data[reg_event])
        sgpa = weighted_sum / sem_credits if sem_credits else 0

        total_weighted_all += weighted_sum
        total_credits_all += sem_credits
        cgpa = total_weighted_all / total_credits_all if total_credits_all else 0

        output_rows.append({
            'student_id': sid,
            'RegEventId': reg_event,
            'semester_credits': round(sem_credits, 2),
            'SGPA': round(sgpa, 2),
            'CGPA_to_date': round(cgpa, 2)
        })

# ---------------- Output ----------------
if not output_rows:
    print(f"No data found for student_id = {student_id_input}")
else:
    print("student_id,RegEventId,semester_credits,SGPA,CGPA_to_date")
    for row in output_rows:
        print(f"{row['student_id']},{row['RegEventId']},{row['semester_credits']},{row['SGPA']},{row['CGPA_to_date']}")

# ---------------- Save to CSV ----------------
import pandas as pd
df = pd.DataFrame(output_rows)
df.to_csv("SGPA_CGPA_all_students.csv", index=False)
print(f"\nSaved output to SGPA_CGPA_all_students.csv")