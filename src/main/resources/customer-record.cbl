       01 CUSTOMER-RECORD.
           03 CUSTOMER-ID          PIC 9(6).
           03 CUSTOMER-NAME.
               05 FIRST-NAME       PIC X(20).
               05 LAST-NAME        PIC X(25).
           03 ADDRESS-INFO.
               05 STREET-ADDRESS   PIC X(40).
               05 CITY             PIC X(25).
               05 STATE            PIC XX.
               05 ZIP-CODE         PIC 9(5).
           03 PHONE-NUMBER         PIC 9(10).
           03 ACCOUNT-BALANCE      PIC S9(7)V99 COMP-3.
           03 ACCOUNT-STATUS       PIC X.
           03 LAST-TRANSACTION-DATE.
               05 TRANS-YEAR       PIC 9999.
               05 TRANS-MONTH      PIC 99.
               05 TRANS-DAY        PIC 99.
           03 FILLER               PIC X(10).
