# COS303 - BE Programmation Mission

Projet Java du BE de programmation mission, basé sur PATRIUS 4.9.1.

## Structure

- `BEProgrammationMission/src/main/java/` : code fourni et code de la mission.
- `BEProgrammationMission/src/test/java/` : classes de lancement, tutoriel et tests.
- `BEProgrammationMission/jars/` : dépendances fournies avec l'environnement du cours, à récupérer via le LMS.
- `Vts-WindowsNT-32bits-3.5.1/` : installation locale de VTS, non versionnée.

## Démarrage

1. Récupérer l'archive `Environnement_de_developpement.zip` depuis le LMS.
2. Importer `BEProgrammationMission` dans Eclipse avec `File > Import > General > Existing Projects Into Workspace`.
3. Configurer un JDK Java 8 dans Eclipse.
4. Exécuter `simulation.SimpleMissionMain` pour vérifier l'environnement.
5. Développer principalement dans `progmission.CompleteMission`.

Les fichiers `jars`, `.classpath` et `.project` fournis par l'enseignement sont nécessaires au projet. Ne pas modifier les dépendances ni les fichiers VTS sans accord du groupe.

## VTS

VTS 3.5.1 est fourni pour Windows et Linux, pas pour macOS. Le développement Java peut être réalisé sur macOS ; la visualisation VTS doit être faite sur une machine Windows/Linux du groupe. Les fichiers générés dans `Vts-*/Data/` restent locaux.

## Collaboration

Ne pas committer les métadonnées Eclipse, les fichiers compilés, les logs, les accès sérialisés ni l'installation VTS. Chaque membre doit récupérer l'environnement officiel depuis le LMS et cloner le dépôt du code.
