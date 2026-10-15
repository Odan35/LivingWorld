// Applique les blocs LivingWorld à une copie vanilla d'un template de rôle Hytale 0.7.2.
// Usage : node tools/patch-templates.js <vanilla.json> <sortie.json> <herbivore|predator|spider>
//
// Le formatage vanilla est conservé : les blocs sont insérés en texte à des ancres précises,
// puis le résultat est validé comme JSON. À relancer après chaque mise à jour de Hytale.
const fs = require('fs');
const [, , src, dst, kind] = process.argv;
let text = fs.readFileSync(src, 'utf8');

// ---------------------------------------------------------------- briques communes
const BIG_PREY = ['Prey', 'PreyBig'];
const SMALL_PREY = ['Prey'];
const ALL_PREDATORS = ['PredatorsBig', 'Predators', 'Vermin'];
const BIG_PREDATORS = ['PredatorsBig'];
const MEAT = ['Food_Wildmeat_Raw', 'Food_Beef_Raw', 'Food_Pork_Raw', 'Food_Chicken_Raw', 'Food_Fish_Raw*'];

const hungry = (diet, threshold = 'LivingWorldHungerThreshold') =>
  ({ Type: 'LivingWorld_Hungry', Diet: diet, Threshold: { Compute: threshold } });
const dietIs = (diet) => ({ Type: 'LivingWorld_Diet', Diet: diet });
const mob = (range, groups, lock) => ({
  Type: 'Mob', Range: { Compute: range }, GetPlayers: false, GetNPCs: true, LockOnTarget: lock,
  Filters: [{ Type: 'NPCGroup', IncludeGroups: groups }]
});
const meat = (range) => ({ Type: 'DroppedItem', Range: range, Items: MEAT });
const event = (name) => ({ Type: 'LivingWorld_NeedsEvent', Event: name });
const state = (name) => ({ Type: 'State', State: name });
const stateIs = (name) => ({ Type: 'State', State: name });
const anim = (name) => (name ? { Type: 'PlayAnimation', Slot: 'Status', Animation: name } : { Type: 'PlayAnimation', Slot: 'Status' });
const param = (value, description) => ({ Value: value, Description: 'LivingWorld : ' + description });

// Le point d'attache (leash) suit le prédateur : sa zone se déplace avec lui, sinon le leash
// vanilla (retour à la zone, abandon de poursuite en combat) le ramènerait à son spawn
const leashHere = { Type: 'SetLeashPosition', ToCurrent: true };
const followLeash = { Type: 'Timeout', Delay: [3, 5], Action: leashHere };

// ---------------------------------------------------------------- herbivores
const herbivoreParams = {
  LivingWorldHungerThreshold: param(40, 'sous cette satiété (0-100), un herbivore part chercher à manger.'),
  LivingWorldWakeThreshold: param(20, 'sous cette satiété, un herbivore endormi se réveille pour brouter.'),
  LivingWorldMealAmount: param(35, 'satiété regagnée par repas de plante.'),
  LivingWorldFoodBlockSet: param('LivingWorld_HerbivoreFood', 'blocs recherchés par un herbivore affamé.'),
  LivingWorldConsumableBlockSet: param('LivingWorld_ConsumablePlants', 'blocs qui disparaissent une fois mangés.'),
  LivingWorldFoodSearchRange: param(32, 'distance de recherche des plantes quand il a faim (au lieu de sa ViewRange).'),
  LivingWorldForageSpeed: param(0.8, 'vitesse relative pour rejoindre une plante quand il a faim.'),
  LivingWorldPredatorAlertRange: param(20, 'distance à laquelle il repère un prédateur et s\'enfuit (première fuite).'),
  LivingWorldPredatorCloseRange: param(10, 'tant qu\'il est en méfiance (après une fuite), il ne re-fuit un prédateur que s\'il repasse sous cette distance. Évite les fuites en boucle quand un prédateur traîne dans la zone.'),
  LivingWorldPredatorCautionRange: param(32, 'tant qu\'un prédateur est à moins de cette distance, il ne part pas chercher à manger.'),
  LivingWorldFearDuration: param(['PT15S', 'PT25S'], 'après une fuite, durée pendant laquelle il ne cherche pas à manger ni ne re-fuit (sauf si un prédateur passe sous LivingWorldPredatorCloseRange).')
};

// Random: false → la plante la plus proche (pas une au hasard, qui pouvait être du côté du prédateur).
// Reserve: true → deux animaux du troupeau ne visent pas la même plante et ne se bousculent pas en mangeant.
// Le sensor Block met sa cible en cache : elle est réinitialisée à chaque fuite (voir flee).
const food = (range) => ({ Type: 'Block', Range: range, MaxHeight: 4, Blocks: { Compute: 'LivingWorldFoodBlockSet' }, Random: false, Reserve: true });
const fearInactive = {
  Type: 'Or',
  Sensors: [
    { Type: 'Alarm', Name: 'LivingWorld_Fear', State: 'Unset' },
    { Type: 'Alarm', Name: 'LivingWorld_Fear', State: 'Passed' }
  ]
};
const fearActive = { Type: 'Not', Sensor: fearInactive };
// Prédateur dangereux pour ce régime à moins de range blocs (même règle que la fuite)
const predatorNear = (range) => ({
  Type: 'Or',
  Sensors: [
    { Type: 'And', Sensors: [dietIs('SmallHerbivore'), mob(range, ALL_PREDATORS, false)] },
    { Type: 'And', Sensors: [dietIs('LargeHerbivore'), mob(range, BIG_PREDATORS, false)] }
  ]
});

const flee = (diet, groups, who) => ({
  $Comment: `LivingWorld : ${who} repère un prédateur (${groups.join('/')}) → fuite immédiate. En vanilla les prédateurs sont Neutral pour les proies : elles les surveillent puis les ignorent, et ne fuient qu'au contact. Première fuite déclenchée à LivingWorldPredatorAlertRange blocs ; pendant la méfiance qui suit (LivingWorldFearDuration), la fuite n'est re-déclenchée que si un prédateur repasse sous LivingWorldPredatorCloseRange blocs — sinon la proie fuyait en boucle toutes les quelques secondes tant qu'un prédateur restait dans la zone.`,
  Sensor: {
    Type: 'And',
    Sensors: [
      dietIs(diet),
      {
        Type: 'Or',
        Sensors: [
          { $Comment: 'Première fuite : pas (ou plus) en méfiance.', Type: 'And', Sensors: [fearInactive, mob('LivingWorldPredatorAlertRange', groups, true)] },
          { $Comment: 'En méfiance, mais un prédateur s\'est rapproché à moins de LivingWorldPredatorCloseRange : on re-fuit.', ...mob('LivingWorldPredatorCloseRange', groups, true) }
        ]
      }
    ]
  },
  Actions: [
    event('FleePredator'),
    { $Comment: 'Oublie la plante visée (souvent du côté du prédateur) : la prochaine recherche partira de l\'endroit où il s\'est réfugié.', Type: 'ResetBlockSensors' },
    { $Comment: 'Prolonge la méfiance (pas de maraude ni de re-fuite au-delà de LivingWorldPredatorCloseRange).', Type: 'SetAlarm', Name: 'LivingWorld_Fear', DurationRange: { Compute: 'LivingWorldFearDuration' } },
    state('Flee')
  ]
});
const FLEES = [
  flee('SmallHerbivore', ALL_PREDATORS, 'petit herbivore'),
  flee('LargeHerbivore', BIG_PREDATORS, 'gros herbivore')
];

const forage = {
  $Comment: 'LivingWorld : recherche de nourriture déclenchée par la faim (sous-état .LivingWorldForage).',
  Sensor: stateIs('.LivingWorldForage'),
  Instructions: [
    {
      $Comment: 'LivingWorld : un prédateur rôde à proximité (ou il vient de fuir) → abandonne la recherche plutôt que d\'aller manger vers lui.',
      Sensor: { Type: 'Or', Sensors: [predatorNear('LivingWorldPredatorCautionRange'), fearActive] },
      Actions: [anim(), event('ForageInterrupted'), { Type: 'ResetBlockSensors' }, state('.Default')]
    },
    {
      $Comment: 'LivingWorld : plante à portée → on la mange sur place, immobile (animation, particules, satiété, bloc consommé).',
      Sensor: food(1.5),
      BodyMotion: { $Comment: 'Immobile pendant le repas (AimCharge, utilisé par le vanilla, le faisait pivoter et avancer).', Type: 'Nothing' },
      HeadMotion: { Type: 'Watch' },
      ActionsBlocking: true,
      Actions: [
        anim('Eat'),
        { Type: 'SpawnParticles', ParticleSystem: 'Want_Food_Lettuce' },
        { Type: 'Timeout', Delay: [3, 4] },
        { Type: 'LivingWorld_Eat', Amount: { Compute: 'LivingWorldMealAmount' }, ConsumeBlock: true, ConsumableBlockSet: { Compute: 'LivingWorldConsumableBlockSet' } },
        anim(),
        { Type: 'ResetBlockSensors' },
        state('.Default')
      ]
    },
    {
      $Comment: 'LivingWorld : plante repérée → y va rapidement.',
      Sensor: food({ Compute: 'LivingWorldFoodSearchRange' }),
      BodyMotion: { Type: 'Seek', RelativeSpeed: { Compute: 'LivingWorldForageSpeed' }, StopDistance: 0.5, RelaxedMoveConstraints: false },
      HeadMotion: { Type: 'Watch' }
    },
    {
      $Comment: 'LivingWorld : rien en vue → explore pendant 10-14 s (les instructions au-dessus reprennent dès qu\'une plante apparaît), puis abandonne et réessaie 10-15 s plus tard.',
      BodyMotion: { Type: 'Wander', MinWalkTime: 2, MaxWalkTime: 4, MaxHeadingChange: 60, RelaxHeadingChange: true, RelativeSpeed: 0.5 },
      Actions: [
        {
          Type: 'Timeout',
          Delay: [10, 14],
          Action: {
            Type: 'Sequence',
            Actions: [
              event('GiveUp'),
              { Type: 'SetAlarm', Name: 'LivingWorld_ForageCooldown', DurationRange: ['PT10S', 'PT15S'] },
              { Type: 'ResetBlockSensors' },
              state('.Default')
            ]
          }
        }
      ]
    }
  ]
};

const forageEntry = {
  $Comment: 'LivingWorld : herbivore affamé, pas de prédateur à proximité, pas en délai de réessai ni effrayé → part manger tout de suite, sans attendre un tirage aléatoire de comportement.',
  Sensor: {
    Type: 'And',
    Sensors: [
      hungry('Herbivore'),
      { Type: 'Not', Sensor: predatorNear('LivingWorldPredatorCautionRange') },
      { Type: 'Not', Sensor: fearActive },
      {
        Type: 'Or',
        Sensors: [
          { Type: 'Alarm', Name: 'LivingWorld_ForageCooldown', State: 'Unset' },
          { Type: 'Alarm', Name: 'LivingWorld_ForageCooldown', State: 'Passed' }
        ]
      }
    ]
  },
  Actions: [event('StartForaging'), state('.LivingWorldForage')]
};

const wakeHerbivore = {
  $Comment: 'LivingWorld : un herbivore très affamé ne reste pas endormi, il se lève pour brouter.',
  Sensor: hungry('Herbivore', 'LivingWorldWakeThreshold'),
  Actions: [event('WakeHungryHerbivore'), state('Idle')]
};

// ---------------------------------------------------------------- prédateurs
const carnivoreParams = {
  LivingWorldHungerThreshold: param(40, 'sous cette satiété (0-100), le prédateur part en chasse.'),
  LivingWorldWakeThreshold: param(20, 'sous cette satiété, un prédateur endormi se réveille pour chasser.'),
  LivingWorldMeatSearchRange: param(24, 'distance à laquelle il repère de la viande au sol.'),
  LivingWorldMeatAmount: param(30, 'satiété regagnée par morceau de viande mangé.'),
  LivingWorldHuntRange: param(26, 'distance à laquelle un prédateur affamé repère une proie et l\'attaque. Doit rester > LivingWorldPredatorAlertRange (fuite des proies à 20 blocs) : sinon la proie fuit avant que la chasse ne démarre, le combat se termine en poursuite ratée, et le cycle recommence.'),
  LivingWorldScentRange: param(48, 'distance à laquelle il flaire une proie et remonte sa piste.'),
  LivingWorldProwlHeadingChange: param(35, 'changement de cap maximal (degrés) entre deux segments de patrouille. Faible = le prédateur garde sa direction et part loin de son spawn.'),
  LivingWorldProwlSpeed: param(0.45, 'vitesse relative en patrouille.'),
  LivingWorldTrackSpeed: param(0.65, 'vitesse relative quand il suit une piste ou va vers de la viande.')
};

const scavenge = {
  $Comment: 'LivingWorld : va manger de la viande au sol (sous-état .LivingWorldScavenge). C\'est ce repas, et non la mise à mort, qui rend de la satiété.',
  Sensor: stateIs('.LivingWorldScavenge'),
  Instructions: [
    {
      $Comment: 'LivingWorld : viande à portée → la mange (l\'objet disparaît). Portée élargie à 2.5 blocs : un gros prédateur (ours) est trop large pour s\'approcher à moins de 1.6 blocs.',
      Sensor: meat(2.5),
      BodyMotion: { $Comment: 'Immobile pendant le repas.', Type: 'Nothing' },
      HeadMotion: { Type: 'Watch' },
      ActionsBlocking: true,
      Actions: [
        anim('Eat'),
        { Type: 'Timeout', Delay: [2, 3] },
        { Type: 'PickUpItem', Range: 2.5, StorageTarget: 'Destroy' },
        { Type: 'LivingWorld_Eat', Amount: { Compute: 'LivingWorldMeatAmount' } },
        anim(),
        state('.Default')
      ]
    },
    {
      $Comment: 'LivingWorld : viande repérée → s\'en approche.',
      Sensor: { ...meat(0), Range: { Compute: 'LivingWorldMeatSearchRange' } },
      BodyMotion: { Type: 'Seek', RelativeSpeed: { Compute: 'LivingWorldTrackSpeed' }, StopDistance: 0.5 },
      HeadMotion: { Type: 'Watch' },
      Actions: [followLeash]
    },
    {
      $Comment: 'LivingWorld : la viande a disparu (mangée par un autre, ramassée…).',
      Sensor: { Type: 'Any' },
      Actions: [event('LostMeat'), state('.Default')]
    }
  ]
};

const scavengeEntry = {
  $Comment: 'LivingWorld : prédateur affamé et viande au sol à portée → va la manger avant de chasser autre chose.',
  Sensor: { Type: 'And', Sensors: [hungry('Carnivore'), { ...meat(0), Range: { Compute: 'LivingWorldMeatSearchRange' } }] },
  Actions: [event('SmellMeat'), state('.LivingWorldScavenge')]
};

const hunt = (diet, groups) => ({
  $Comment: `LivingWorld : ${diet} affamé avec une proie (${groups.join('/')}) à portée → attaque immédiate. Placé avant la détection standard, qui ne ferait que passer en Alerted (les proies sont Neutral pour les prédateurs). Une fois la proie tuée, il mange la viande qu'elle lâche.`,
  Sensor: { Type: 'And', Sensors: [hungry(diet), mob('LivingWorldHuntRange', groups, true)] },
  Actions: [
    event('StartHunting'),
    { $Comment: 'La proie devient hostile pour ce prédateur le temps de la chasse, sinon le combat la relâcherait.', Type: 'OverrideAttitude', Attitude: 'Hostile', Duration: 60 },
    { $Comment: 'Sa zone devient l\'endroit de la chasse : la poursuite ne sera pas abandonnée pour rentrer au spawn.', ...leashHere },
    state('Combat.Message')
  ]
});

const scentOfPrey = {
  Type: 'Or',
  Sensors: [
    { Type: 'And', Sensors: [hungry('Predator'), mob('LivingWorldScentRange', BIG_PREY, false)] },
    { Type: 'And', Sensors: [hungry('SmallPredator'), mob('LivingWorldScentRange', SMALL_PREY, false)] }
  ]
};

const satiatedExit = {
  $Comment: 'LivingWorld : rassasié → fin de la chasse.',
  Sensor: { Type: 'Not', Sensor: hungry('Carnivore') },
  Actions: [event('StopProwling'), state('.Default')]
};

const track = {
  $Comment: 'LivingWorld : remonte la piste d\'une proie flairée (sous-état .LivingWorldTrack). Dès qu\'elle est à portée de chasse, l\'instruction de chasse plus haut prend le relais.',
  Sensor: stateIs('.LivingWorldTrack'),
  Instructions: [
    satiatedExit,
    {
      Sensor: scentOfPrey,
      BodyMotion: { Type: 'Seek', RelativeSpeed: { Compute: 'LivingWorldTrackSpeed' }, StopDistance: 2 },
      Actions: [followLeash],
      HeadMotion: { Type: 'Watch' }
    },
    {
      $Comment: 'LivingWorld : piste perdue → reprend la patrouille.',
      Sensor: { Type: 'Any' },
      Actions: [event('LostTrack'), state('.LivingWorldProwl')]
    }
  ]
};

const prowl = {
  $Comment: 'LivingWorld : patrouille de chasse (sous-état .LivingWorldProwl) : voyage en gardant à peu près sa direction, avec des pauses pour flairer, jusqu\'à sentir une proie ou de la viande. Sa zone le suit : il quitte réellement son spawn et s\'installe là où il finit par manger.',
  Sensor: stateIs('.LivingWorldProwl'),
  Instructions: [
    satiatedExit,
    {
      $Comment: 'LivingWorld : une proie est flairée → suit sa piste.',
      Sensor: scentOfPrey,
      Actions: [event('TrackPrey'), state('.LivingWorldTrack')]
    },
    {
      $Comment: 'LivingWorld : rien senti → avance par segments en ligne droite (Wander, pas WanderInCircle qui tourne autour du spawn), puis s\'arrête pour flairer.',
      Actions: [followLeash],
      BodyMotion: {
        Type: 'Sequence',
        Looped: true,
        Motions: [
          { Type: 'Timer', Time: [6, 12], Motion: { Type: 'Wander', MinWalkTime: 3, MaxWalkTime: 6, MaxHeadingChange: { Compute: 'LivingWorldProwlHeadingChange' }, RelaxHeadingChange: true, RelativeSpeed: { Compute: 'LivingWorldProwlSpeed' } } },
          { Type: 'Timer', Time: [1.5, 3], Motion: { Type: 'Nothing' } }
        ]
      }
    }
  ]
};

const prowlEntry = {
  $Comment: 'LivingWorld : prédateur affamé sans proie ni viande en vue → part en patrouille. Placé avant le leash vanilla, qui le ramènerait à sa zone.',
  Sensor: hungry('Carnivore'),
  Actions: [event('StartProwling'), state('.LivingWorldProwl')]
};

const wakeCarnivore = {
  $Comment: 'LivingWorld : un prédateur très affamé ne reste pas endormi, il se lève pour chasser.',
  Sensor: hungry('Carnivore', 'LivingWorldWakeThreshold'),
  Actions: [event('WakeHungry'), state('Idle')]
};

const CARNIVORE_FIRST = [scavenge, scavengeEntry, hunt('Predator', BIG_PREY), hunt('SmallPredator', SMALL_PREY)];
const CARNIVORE_ROAM = [track, prowl, prowlEntry];

// ---------------------------------------------------------------- insertion textuelle
function render(obj, indent) {
  return JSON.stringify(obj, null, 2).split('\n').map((l) => ' '.repeat(indent) + l).join('\n');
}
function find(anchor, from = 0) {
  const at = text.indexOf(anchor, from);
  if (at < 0) throw new Error('Ancre introuvable : ' + anchor);
  return at;
}
// Insère des éléments de tableau juste avant l'objet « { » qui contient l'ancre ; renvoie la position après insertion
function insertBeforeObjectContaining(anchor, blocks, from = 0) {
  const at = find(anchor, from);
  const anchorLineStart = text.lastIndexOf('\n', at) + 1;
  const braceLineEnd = anchorLineStart - 1;
  const braceLineStart = text.lastIndexOf('\n', braceLineEnd - 1) + 1;
  const braceLine = text.slice(braceLineStart, braceLineEnd);
  if (braceLine.trim() !== '{') throw new Error('Pas de « { » avant l\'ancre : ' + anchor);
  const indent = braceLine.indexOf('{');
  const inserted = blocks.map((b) => render(b, indent) + ',\n').join('');
  text = text.slice(0, braceLineStart) + inserted + text.slice(braceLineStart);
  return braceLineStart + inserted.length;
}
// Insère des éléments au début du tableau "Instructions" qui suit l'ancre
function insertAtInstructionsStart(anchor, blocks) {
  const arr = find('"Instructions": [', find(anchor));
  const lineEnd = text.indexOf('\n', arr) + 1;
  const nextLine = text.slice(lineEnd, text.indexOf('\n', lineEnd));
  const indent = nextLine.indexOf('{');
  const inserted = blocks.map((b) => render(b, indent) + ',\n').join('');
  text = text.slice(0, lineEnd) + inserted + text.slice(lineEnd);
}
function addParameters(params) {
  const m = /^  "Parameters": \{\n/m.exec(text);
  if (!m) throw new Error('Paramètres racine introuvables');
  const entries = Object.entries(params)
    .map(([k, v]) => '    ' + JSON.stringify(k) + ': { "Value": ' + JSON.stringify(v.Value) + ', "Description": ' + JSON.stringify(v.Description) + ' },\n')
    .join('');
  const at = m.index + m[0].length;
  text = text.slice(0, at) + entries + text.slice(at);
}
function addRootComment(note) {
  const before = text;
  text = text.replace(/^\{\n  "Type": "Abstract",\n/, (s) => s + '  "$Comment": ' + JSON.stringify(note) + ',\n');
  if (text === before) throw new Error('Début de template inattendu');
}

const NOTE = 'LivingWorld : copie du template vanilla 0.7.2 patchée par tools/patch-templates.js. Les ajouts sont les blocs dont le $Comment commence par « LivingWorld ». Les sensors LivingWorld ne réagissent qu\'aux rôles classés par régime (groupes Prey, PreyBig, PredatorsBig, Predators, Vermin) : les autres rôles qui partagent ce template ne sont pas concernés.';
const IDLE = '"State": "Idle"\n          },\n          "Instructions"';
const SLEEP = '"State": "Sleep"\n          },\n          "Instructions"';
const LEASH = '"$Comment": "If we\'re beyond our soft leash radius';

addRootComment(NOTE);
if (kind === 'herbivore') {
  addParameters(herbivoreParams);
  const idle = find(IDLE);
  insertBeforeObjectContaining('"$Comment": "Check if there are any potential things that can alert the NPC"', FLEES, idle);
  insertBeforeObjectContaining('"Sensor": {\n                "Type": "State",\n                "State": ".FindFood"', [forage, forageEntry], idle);
  insertAtInstructionsStart('"State": "Alerted"\n          },\n          "Instructions"', FLEES);
  insertAtInstructionsStart(SLEEP, [wakeHerbivore]);
} else if (kind === 'predator') {
  addParameters(carnivoreParams);
  const after = insertBeforeObjectContaining('"$Comment": "Check if there are any potential threats nearby"', CARNIVORE_FIRST, find(IDLE));
  insertBeforeObjectContaining(LEASH, CARNIVORE_ROAM, after);
  insertAtInstructionsStart(SLEEP, [wakeCarnivore]);
} else if (kind === 'spider') {
  addParameters(carnivoreParams);
  const after = insertBeforeObjectContaining('"$Comment": "Generic detection: sight, sound, beacons and damage"', CARNIVORE_FIRST);
  insertBeforeObjectContaining(LEASH, CARNIVORE_ROAM, after);
} else {
  throw new Error('Type inconnu : ' + kind);
}

JSON.parse(text); // échoue si le résultat n'est pas un JSON valide
fs.writeFileSync(dst, text);
console.log('OK', kind, dst);
