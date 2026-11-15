// Dirección del servidor (Spring Boot). Si lo despliegas en la nube, cambia SOLO esta línea.
const SERVIDOR = 'http://localhost:8080';

export const API = SERVIDOR;
export const WS_URL = SERVIDOR.replace(/^http/, 'ws') + '/ws';
