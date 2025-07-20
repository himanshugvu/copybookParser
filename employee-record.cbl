       01 BTBMS-RECORD             PIC X(250)
       01 EMPLOYEE-HDR-RECORD REDEFINES BTBMS-RECORD.
           03 EMP-ID               PIC 9(8) COMP.
           03 EMP-NAME             PIC X(50).
           03 FILLER               PIC X(192)
       01 EMPLOYEE-DATA-RECORD REDEFINES BTBMS-RECORD.
           03 EMP-SALARY           PIC 9(7)V99 COMP-3.
           03 EMP-HIRE-DATE        PIC 9(8) COMP.
           03 EMP-STATUS           PIC X.
               88 ACTIVE           VALUE 'A'.
               88 INACTIVE         VALUE 'I'.
               88 TERMINATED       VALUE 'T'.
           03 EMP-BENEFITS         OCCURS 5 TIMES.
               05 BENEFIT-CODE     PIC XX.
               05 BENEFIT-AMOUNT   PIC 9(5)V99 COMP-3.
           03 EMP-TEST             PIC X(10).
       01 EMPLOYEE-TRAIL-RECORD REDEFINES BTBMS-RECORD.
           03 EMP-DEPT             PIC X(10).
           03 FILLER               PIC X(192)
