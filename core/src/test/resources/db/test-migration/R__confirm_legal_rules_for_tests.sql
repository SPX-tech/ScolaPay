-- Uniquement dans la base jetable de la suite principale, qui a besoin d'un portail ouvert.
-- GoLiveTest vérifie le comportement sans cette confirmation.
UPDATE legal_majority_rules SET legally_confirmed = true;
