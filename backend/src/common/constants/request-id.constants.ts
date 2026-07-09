/** Header de corrélation, accepté en entrée et toujours renvoyé en sortie. */
export const REQUEST_ID_HEADER = 'x-request-id';

/**
 * Un identifiant de corrélation fourni par le client n'est réutilisé que s'il
 * est inoffensif : jeu de caractères restreint (donc ni CR/LF — log forging,
 * header splitting — ni séquences ANSI) et longueur bornée. Sinon, on en génère
 * un nouveau.
 */
export const SAFE_REQUEST_ID_PATTERN = /^[A-Za-z0-9._-]{8,64}$/;
