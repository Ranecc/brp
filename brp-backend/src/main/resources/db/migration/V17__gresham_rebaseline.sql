-- V17: Gresham rebaseline (owner review): the absolute-share rule name
-- retired — the alarm is now per-rail moving-baseline divergence.
UPDATE anomaly_rules SET name = 'BRP-0903 Gresham: industrial rail collapsing vs own baseline'
    WHERE id = 903;
