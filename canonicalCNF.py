import sys
import subprocess

# Check_output() not defined in Python 2.6
def check_output(*popenargs, **kwargs):
    if 'stdout' in kwargs:
        raise ValueError('stdout argument not allowed, it will be overridden.')
    process = subprocess.Popen(stdout=subprocess.PIPE, *popenargs, **kwargs)
    output, unused_err = process.communicate()
    retcode = process.poll()
    if retcode:
        cmd = kwargs.get("args")
        if cmd is None:
            cmd = popenargs[0]
        raise subprocess.CalledProcessError(retcode, cmd, output=output)
    return output

class CalledProcessError(Exception):
    def __init__(self, returncode, cmd, output=None):
        self.returncode = returncode
        self.cmd = cmd
        self.output = output
    def __str__(self):
        return "Command '%s' returned non-zero exit status %d" % (
            self.cmd, self.returncode)
# overwrite CalledProcessError due to `output` keyword might be not available
subprocess.CalledProcessError = CalledProcessError

def reduceClause(clause, backbones):
  newClause = Clause(clause.weight)
  newClause.lits = filter(lambda l: -l not in backbones, clause.lits)
  return newClause

class WCNF:
  def __init__(self, hard_weight):
    self.hard_weight = hard_weight
    self.clauses = [] #set()
  def setClauses(self, clauses):
    self.clauses = clauses
  def addClause(self,clause):
    self.clauses.append(clause) #add(clause)
  def applyBackbones(self, backbones):
    print backbones
    usefulClauses = filter(lambda c: not any([l in c.lits for l in backbones]), self.clauses)
    print usefulClauses
    reducedClauses = map(lambda c: reduceClause(c, backbones), usefulClauses)
    print reducedClauses
    self.clauses = reducedClauses
  def __str__(self):
    clauseList = [c for c in self.clauses]
    clauseList.sort(key=lambda x: x.weight)
    clauseList.sort(key=lambda x: x.__str__())
    return "Hard weight: %f\nNumber clauses: %d\nClauses:\n%s" \
      % (self.hard_weight, len(self.clauses), \
      "\n".join([c.__str__() for c in clauseList]))

class Clause:
  def __init__(self,weight):
    self.weight=weight
    self.lits=[] #set()
  def addLiteral(self,lit):
    self.lits.append(lit) #add(lit)
  def __repr__(self):
    return "[" + " ".join([str(l) for l in self.lits]) + "]"
  def __str__(self):
    literalList = [l for l in self.lits]
    literalList.sort()
    return "Weight: %f Literals: %s" % (self.weight, \
      " ".join([str(l) for l in literalList]))

def runGlucose(filename):
  output = check_output(
    "/home/ec2-user/glucose-3.0-backbone/core/glucose -printunits "  \
    + filename + " | grep UNITS", shell=True
  )
  backbones = [int(l) for l in output.split(" ")[1:]]
  return backbones

def readWCNF(filename):
  lines = [line.rstrip() for line in open(filename)]
  header = lines[0].split(" ")
  hard_weight = float(header[4])

  wcnf = WCNF(hard_weight)

  for i in range(1, len(lines)):
    (weight, rest) = lines[i].split(" ", 1)
    weight = float(weight)
    clause = Clause(weight)
    (lits, zero) = rest.rsplit(" ", 1)
    for l in lits.split(" "):
      clause.addLiteral(int(l))
    wcnf.addClause(clause)

  return wcnf

def writeHardClausesToFile(wcnf, filename):
  f = open(filename, 'w')
  f.write("p cnf %d %d\n" % (1, len(wcnf.clauses)))
  for c in wcnf.clauses:
    if c.weight >= wcnf.hard_weight: 
      f.write("%s 0\n" % (" ".join([str(l) for l in c.lits])))
    if c.weight <= -wcnf.hard_weight:
      print "uh oh"
      exit

  f.close()

def reduceClauses(clauses, backbones):
  usefulClauses = filter(lambda c: not any([l in c for l in backbones]), clauses)
  reducedClauses = map(lambda c: filter(lambda l: -l not in backbones, c), usefulClauses)
  return reducedClauses

wcnfFilename = sys.argv[1]
cnfFilename = sys.argv[2]
canonicalWcnfFilename = sys.argv[3]
wcnf = readWCNF(wcnfFilename)
writeHardClausesToFile(wcnf, cnfFilename)
#print readWCNF(filename)
backbones = runGlucose(cnfFilename)
wcnf.applyBackbones(backbones)
print wcnf



