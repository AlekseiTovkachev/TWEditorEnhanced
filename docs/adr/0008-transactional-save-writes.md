# Validate a complete candidate before replacing a Save

Writing directly over a Save makes a late I/O or encoding failure capable of destroying the user's only good copy. Every Save operation will instead build a candidate beside the original, reload it through the real parser, verify its archive structure, and only then atomically replace the destination. If safe replacement is unavailable or any earlier stage fails, the original remains untouched, the in-memory draft stays dirty, and the candidate is retained for diagnosis.

The existing first-write backup remains an independent recovery layer rather than a substitute for transactional replacement. Failure-injection tests must cover candidate creation, encoding, validation, and replacement, and prove at every point that the original Save is still readable and byte-identical.
