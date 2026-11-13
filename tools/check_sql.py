"""Comprobación SQL parcial. No sustituye la compilación ni Room instrumentado."""
from pathlib import Path
import re, sqlite3
root = Path(__file__).resolve().parents[1]
source = (root/'app/src/main/java/com/rafa/hercules/data/AppDatabase.kt').read_text()
fixture = (root/'app/src/androidTest/java/com/rafa/hercules/data/SchemaMigrationTest.kt').read_text()
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
for sql in re.findall(r'old.execSQL\("([^"\n]*)"\)', fixture):
    if sql.startswith(('CREATE TABLE', 'INSERT INTO')): db.execute(sql)
for table,col in [('routine_exercises','routineId'),('routine_exercises','exerciseId'),('series','sessionId'),('series','exerciseId')]:
    db.execute(f'CREATE INDEX index_{table}_{col} ON {table}({col})')
before = db.execute('SELECT id,weight,reps,isCompleted,completedAt FROM series ORDER BY id').fetchall()
body = source.split('val MIGRATION_9_10 =')[1].split('@Database')[0]
fields = re.findall(r'"(\w+)" to "(TEXT|INTEGER)"',body)
checks = 0
for name,kind in fields:
    db.execute(f'ALTER TABLE series ADD COLUMN {name} {kind}')
    checks += 1
for sql in re.findall(r'db.execSQL\("([^"\n]*)"\)', body):
    if sql.startswith('UPDATE'):
        if '$name' in sql:
            for name in ['targetReps','targetRestMs','targetDurationMs','targetDropsets']:
                db.execute(sql.replace('$name',name))
        else: db.execute(sql)
assert before == db.execute('SELECT id,weight,reps,isCompleted,completedAt FROM series ORDER BY id').fetchall(); checks += 1
assert db.execute("SELECT roundIndex,targetRestMs,stageIndex FROM series WHERE id='p'").fetchone() == (0,120000,0); checks += 1
assert db.execute("SELECT roundIndex,groupId,stageIndex FROM series WHERE id='d'").fetchone() == (0,'g',1); checks += 1
assert db.execute('PRAGMA foreign_key_check').fetchall() == []; checks += 1
# Localiza la consulta por su función: el orden de las @Query del archivo puede cambiar.
idx = source.index('suspend fun getPreviousSeriesForExercise')
start = source.rfind('@Query("""', 0, idx)
marker = '@Query("""'
q = source[start + len(marker):source.index('"""', start + len(marker))]
db.execute("INSERT INTO sessions VALUES ('newer','r',2500,2700,'FINISHED',NULL,NULL)")
db.execute("INSERT INTO series(id,sessionId,exerciseId,isWarmup,unit,isCompleted,orderIndex) VALUES ('incomplete','newer','e',0,'kg',0,0)")
returned = db.execute(q,dict(exerciseId='e',excludingSessionId='now',beforeStartTime=3000)).fetchall()
assert {row[0] for row in returned} == {'w','p','d'}; checks += 1
assert db.execute(q,dict(exerciseId='e',excludingSessionId='s',beforeStartTime=1000)).fetchall() == []; checks += 1
db.commit()
try:
    with db:
        db.execute("INSERT INTO sessions VALUES ('partial',NULL,4000,NULL,'ACTIVE',NULL,NULL)")
        db.execute("INSERT INTO series(id,sessionId,exerciseId,isWarmup,unit,isCompleted,orderIndex) VALUES ('bad','partial','missing',0,'kg',0,0)")
except sqlite3.IntegrityError: pass
else: raise AssertionError('El registro inválido debía fallar')
assert db.execute("SELECT COUNT(*) FROM sessions WHERE id='partial'").fetchone()[0] == 0; checks += 1
# Ejecuta la migración nueva sobre las filas ya migradas a v10/v11.
before_reps = db.execute('SELECT id,targetReps FROM routine_exercises ORDER BY id').fetchall()
before_series = db.execute('SELECT id,weight,reps FROM series ORDER BY id').fetchall()
range_migration = source.split('val MIGRATION_11_12 =')[1].split('@Database')[0]
for sql in re.findall(r'db.execSQL\("([^"\n]*)"\)', range_migration):
    db.execute(sql)
assert db.execute('SELECT id,targetReps,targetRepsMax FROM routine_exercises ORDER BY id').fetchall() == [(*r, None) for r in before_reps]; checks += 1
assert db.execute('SELECT id,weight,reps,targetRepsMax FROM series ORDER BY id').fetchall() == [(*r, None) for r in before_series]; checks += 1
print(f'PASS: {checks} comprobaciones SQL parciales. Sin ejecutar código Kotlin, KSP o Room.')
