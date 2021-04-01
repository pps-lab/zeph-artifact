import sys, json, shutil, os

assert(len(sys.argv) == 3)


application = sys.argv[1]

baseDir = sys.argv[2]

print(f"Switching to {application} application!")


# Merge application specific schemas with base schemas
with open(f"{baseDir}/src/main/avro/schema_base.json") as f:
    base_schema = json.load(f)

with open(f"{baseDir}/applications/{application}/schema.avsc") as f:
    app_schema = json.load(f)

schema = [base_schema[0]]
schema += app_schema
schema += base_schema[1:]

with open(f"{baseDir}/src/main/avro/schemas.avsc", "w") as f:
    json.dump(schema, f, indent=2)


# Exchange the Application Adapter
filename = f"{baseDir}/src/main/avro/ch/ethz/infk/pps/shared/avro/ApplicationAdapter.java"
os.makedirs(os.path.dirname(filename), exist_ok=True)
shutil.copyfile(f"{baseDir}/applications/{application}/ApplicationAdapter.java", filename)




