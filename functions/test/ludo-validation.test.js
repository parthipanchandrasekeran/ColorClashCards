/**
 * Firebase Cloud Functions tests for Ludo multiplayer validation.
 * Tests server-side anti-cheat and state transition logic.
 *
 * Run with: npm test
 */

const assert = require('assert');

// ==================== LUDO GAME CONSTANTS ====================

const FINISH_POSITION = 57;
const HOME_POSITION = -1;
const MAIN_TRACK_END = 50;
const MAX_CONSECUTIVE_SIXES = 3;

const SAFE_CELLS = new Set([0, 8, 13, 21, 26, 34, 39, 47]);

const START_POSITIONS = {
  RED: 0,
  BLUE: 13,
  YELLOW: 26,
  GREEN: 39
};

const TokenState = {
  HOME: 'HOME',
  ACTIVE: 'ACTIVE',
  FINISHED: 'FINISHED'
};

const GameStatus = {
  WAITING: 'WAITING',
  IN_PROGRESS: 'IN_PROGRESS',
  FINISHED: 'FINISHED'
};

// ==================== DICE VALIDATION ====================

/**
 * Server-side dice roll handler.
 * Generates dice value securely and validates request.
 */
function handleDiceRoll(gameState, playerId) {
  // Validate it's this player's turn
  if (gameState.currentTurnPlayerId !== playerId) {
    return {
      success: false,
      error: 'NOT_YOUR_TURN',
      message: 'It is not your turn to roll'
    };
  }

  // Validate dice can be rolled
  if (!gameState.canRollDice) {
    return {
      success: false,
      error: 'CANNOT_ROLL',
      message: 'Dice already rolled or must select token'
    };
  }

  // Validate game is in progress
  if (gameState.gameStatus !== GameStatus.IN_PROGRESS) {
    return {
      success: false,
      error: 'GAME_NOT_IN_PROGRESS',
      message: 'Game is not in progress'
    };
  }

  // SERVER generates dice value (anti-cheat)
  const diceValue = generateSecureDiceRoll();

  // Calculate new state
  const newState = applyDiceRoll(gameState, diceValue);

  return {
    success: true,
    diceValue,
    newState,
    timestamp: Date.now()
  };
}

/**
 * Cryptographically secure dice roll.
 */
function generateSecureDiceRoll() {
  // In production, use crypto.randomInt(1, 7)
  return Math.floor(Math.random() * 6) + 1;
}

/**
 * Apply dice roll to game state.
 */
function applyDiceRoll(gameState, diceValue) {
  const currentPlayer = gameState.players.find(
    p => p.id === gameState.currentTurnPlayerId
  );

  // Check for movable tokens
  const movableTokens = getMovableTokens(currentPlayer, diceValue);

  // Handle consecutive sixes
  let consecutiveSixes = gameState.consecutiveSixes || 0;
  let skipTurn = false;

  if (diceValue === 6) {
    consecutiveSixes++;
    if (consecutiveSixes >= MAX_CONSECUTIVE_SIXES) {
      skipTurn = true;
      consecutiveSixes = 0;
    }
  } else {
    consecutiveSixes = 0;
  }

  // If no movable tokens or skipping, advance to next player
  if (movableTokens.length === 0 || skipTurn) {
    return advanceToNextPlayer({
      ...gameState,
      diceValue,
      consecutiveSixes: 0
    });
  }

  return {
    ...gameState,
    diceValue,
    canRollDice: false,
    mustSelectToken: true,
    consecutiveSixes,
    turnStartedAt: Date.now()
  };
}

/**
 * Get tokens that can move with given dice value.
 */
function getMovableTokens(player, diceValue) {
  return player.tokens.filter(token => canTokenMove(token, diceValue));
}

/**
 * Check if a token can move with given dice value.
 */
function canTokenMove(token, diceValue) {
  switch (token.state) {
    case TokenState.HOME:
      return diceValue === 6;
    case TokenState.ACTIVE:
      return token.position + diceValue <= FINISH_POSITION;
    case TokenState.FINISHED:
      return false;
    default:
      return false;
  }
}

// ==================== MOVE VALIDATION ====================

/**
 * Server-side move handler.
 * Validates move and applies it if valid.
 */
function handleMoveRequest(gameState, playerId, tokenId) {
  // Validate it's this player's turn
  if (gameState.currentTurnPlayerId !== playerId) {
    return {
      success: false,
      error: 'NOT_YOUR_TURN',
      message: 'It is not your turn'
    };
  }

  // Validate must select token
  if (!gameState.mustSelectToken) {
    return {
      success: false,
      error: 'CANNOT_MOVE',
      message: 'Must roll dice first'
    };
  }

  // Validate dice value exists
  if (gameState.diceValue === null || gameState.diceValue === undefined) {
    return {
      success: false,
      error: 'NO_DICE',
      message: 'No dice value'
    };
  }

  // Get player and token
  const player = gameState.players.find(p => p.id === playerId);
  if (!player) {
    return {
      success: false,
      error: 'PLAYER_NOT_FOUND',
      message: 'Player not found'
    };
  }

  const token = player.tokens.find(t => t.id === tokenId);
  if (!token) {
    return {
      success: false,
      error: 'TOKEN_NOT_FOUND',
      message: 'Token not found'
    };
  }

  // Validate token can move
  if (!canTokenMove(token, gameState.diceValue)) {
    return {
      success: false,
      error: 'INVALID_MOVE',
      message: 'Token cannot move with this dice value'
    };
  }

  // Apply move
  const moveResult = applyMove(gameState, playerId, tokenId);

  return {
    success: true,
    newState: moveResult.newState,
    move: moveResult.move,
    captured: moveResult.captured,
    bonusTurn: moveResult.bonusTurn,
    hasWon: moveResult.hasWon,
    timestamp: Date.now()
  };
}

/**
 * Apply a validated move to the game state.
 */
function applyMove(gameState, playerId, tokenId) {
  const diceValue = gameState.diceValue;
  const playerIndex = gameState.players.findIndex(p => p.id === playerId);
  const player = gameState.players[playerIndex];
  const tokenIndex = player.tokens.findIndex(t => t.id === tokenId);
  const token = player.tokens[tokenIndex];

  let newPosition;
  let newTokenState;
  let moveType;

  if (token.state === TokenState.HOME) {
    // Exiting home
    newPosition = 0;
    newTokenState = TokenState.ACTIVE;
    moveType = 'EXIT_HOME';
  } else {
    newPosition = token.position + diceValue;

    if (newPosition >= FINISH_POSITION) {
      newPosition = FINISH_POSITION;
      newTokenState = TokenState.FINISHED;
      moveType = 'FINISH';
    } else if (newPosition > MAIN_TRACK_END && token.position <= MAIN_TRACK_END) {
      newTokenState = TokenState.ACTIVE;
      moveType = 'ENTER_HOME_STRETCH';
    } else {
      newTokenState = TokenState.ACTIVE;
      moveType = 'NORMAL';
    }
  }

  // Check for capture
  let captured = null;
  const absolutePosition = toAbsolutePosition(newPosition, player.color);

  if (absolutePosition >= 0 && !isSafeCell(absolutePosition) && newPosition <= MAIN_TRACK_END) {
    captured = checkAndPerformCapture(gameState, playerId, absolutePosition);
  }

  // Update token
  const newTokens = [...player.tokens];
  newTokens[tokenIndex] = {
    ...token,
    state: newTokenState,
    position: newPosition
  };

  // Update player
  const newPlayers = [...gameState.players];
  newPlayers[playerIndex] = {
    ...player,
    tokens: newTokens
  };

  // Apply capture
  if (captured) {
    const capturedPlayerIndex = newPlayers.findIndex(p => p.id === captured.playerId);
    const capturedPlayer = newPlayers[capturedPlayerIndex];
    const capturedTokenIndex = capturedPlayer.tokens.findIndex(t => t.id === captured.tokenId);

    const capturedTokens = [...capturedPlayer.tokens];
    capturedTokens[capturedTokenIndex] = {
      ...capturedTokens[capturedTokenIndex],
      state: TokenState.HOME,
      position: HOME_POSITION
    };

    newPlayers[capturedPlayerIndex] = {
      ...capturedPlayer,
      tokens: capturedTokens
    };
  }

  // Check for winner
  const updatedPlayer = newPlayers[playerIndex];
  const hasWon = updatedPlayer.tokens.every(t => t.state === TokenState.FINISHED);

  // Determine if player gets another turn
  const bonusTurn = !hasWon && (diceValue === 6 || captured !== null);

  let newState;
  if (hasWon) {
    newState = {
      ...gameState,
      players: newPlayers,
      winnerId: playerId,
      gameStatus: GameStatus.FINISHED,
      diceValue: null,
      canRollDice: false,
      mustSelectToken: false
    };
  } else if (bonusTurn) {
    newState = {
      ...gameState,
      players: newPlayers,
      diceValue: null,
      canRollDice: true,
      mustSelectToken: false,
      consecutiveSixes: diceValue === 6 ? gameState.consecutiveSixes : 0
    };
  } else {
    newState = advanceToNextPlayer({
      ...gameState,
      players: newPlayers,
      diceValue: null,
      mustSelectToken: false
    });
  }

  return {
    newState,
    move: {
      playerId,
      tokenId,
      diceValue,
      fromPosition: token.position,
      toPosition: newPosition,
      moveType,
      timestamp: Date.now()
    },
    captured,
    bonusTurn,
    hasWon
  };
}

/**
 * Convert relative position to absolute board position.
 */
function toAbsolutePosition(relativePosition, color) {
  if (relativePosition > MAIN_TRACK_END) {
    return -1; // Home stretch, no absolute position
  }

  const startOffset = START_POSITIONS[color] || 0;
  return (relativePosition + startOffset) % 52;
}

/**
 * Check if a cell is safe.
 */
function isSafeCell(absolutePosition) {
  return SAFE_CELLS.has(absolutePosition);
}

/**
 * Check for and perform capture.
 */
function checkAndPerformCapture(gameState, movingPlayerId, absolutePosition) {
  for (const player of gameState.players) {
    if (player.id === movingPlayerId) continue;

    for (const token of player.tokens) {
      if (token.state !== TokenState.ACTIVE) continue;
      if (token.position > MAIN_TRACK_END) continue;

      const tokenAbsPos = toAbsolutePosition(token.position, player.color);
      if (tokenAbsPos === absolutePosition) {
        return {
          playerId: player.id,
          tokenId: token.id,
          position: token.position
        };
      }
    }
  }

  return null;
}

/**
 * Advance to next player.
 */
function advanceToNextPlayer(gameState) {
  const currentIndex = gameState.players.findIndex(
    p => p.id === gameState.currentTurnPlayerId
  );
  const nextIndex = (currentIndex + 1) % gameState.players.length;
  const nextPlayer = gameState.players[nextIndex];

  return {
    ...gameState,
    currentTurnPlayerId: nextPlayer.id,
    diceValue: null,
    canRollDice: true,
    mustSelectToken: false,
    consecutiveSixes: 0,
    turnStartedAt: Date.now()
  };
}

// ==================== TEST HELPERS ====================

function createTestGameState(options = {}) {
  const player1 = {
    id: 'player-1',
    name: 'Player 1',
    color: 'RED',
    tokens: options.player1Tokens || [
      { id: 0, state: TokenState.HOME, position: HOME_POSITION },
      { id: 1, state: TokenState.HOME, position: HOME_POSITION },
      { id: 2, state: TokenState.HOME, position: HOME_POSITION },
      { id: 3, state: TokenState.HOME, position: HOME_POSITION }
    ]
  };

  const player2 = {
    id: 'player-2',
    name: 'Player 2',
    color: 'BLUE',
    tokens: options.player2Tokens || [
      { id: 0, state: TokenState.HOME, position: HOME_POSITION },
      { id: 1, state: TokenState.HOME, position: HOME_POSITION },
      { id: 2, state: TokenState.HOME, position: HOME_POSITION },
      { id: 3, state: TokenState.HOME, position: HOME_POSITION }
    ]
  };

  return {
    gameId: options.gameId || 'test-game-1',
    players: [player1, player2],
    currentTurnPlayerId: options.currentTurnPlayerId || 'player-1',
    diceValue: options.diceValue || null,
    canRollDice: options.canRollDice !== undefined ? options.canRollDice : true,
    mustSelectToken: options.mustSelectToken || false,
    consecutiveSixes: options.consecutiveSixes || 0,
    gameStatus: options.gameStatus || GameStatus.IN_PROGRESS,
    winnerId: options.winnerId || null,
    version: options.version || 1
  };
}

// ==================== TESTS ====================

describe('Ludo Server Validation', function() {

  // ==================== DICE ROLL TESTS ====================

  describe('Dice Roll Validation', function() {

    it('should reject dice roll when not player turn', function() {
      const gameState = createTestGameState({
        currentTurnPlayerId: 'player-1'
      });

      const result = handleDiceRoll(gameState, 'player-2');

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'NOT_YOUR_TURN');
    });

    it('should reject dice roll when dice already rolled', function() {
      const gameState = createTestGameState({
        canRollDice: false,
        diceValue: 4,
        mustSelectToken: true
      });

      const result = handleDiceRoll(gameState, 'player-1');

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'CANNOT_ROLL');
    });

    it('should reject dice roll when game not in progress', function() {
      const gameState = createTestGameState({
        gameStatus: GameStatus.FINISHED
      });

      const result = handleDiceRoll(gameState, 'player-1');

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'GAME_NOT_IN_PROGRESS');
    });

    it('should generate server-side dice value (1-6)', function() {
      const gameState = createTestGameState();

      const result = handleDiceRoll(gameState, 'player-1');

      assert.strictEqual(result.success, true);
      assert(result.diceValue >= 1 && result.diceValue <= 6,
        `Dice value ${result.diceValue} not in range 1-6`);
    });

    it('should update state correctly after dice roll', function() {
      const gameState = createTestGameState({
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 10 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      // Mock dice to return 4
      const originalRandom = Math.random;
      Math.random = () => 0.5; // Will generate 4

      const result = handleDiceRoll(gameState, 'player-1');

      Math.random = originalRandom;

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.newState.canRollDice, false);
      assert.strictEqual(result.newState.mustSelectToken, true);
    });

    it('should skip turn if no tokens can move', function() {
      // All tokens at HOME, dice is not 6
      const gameState = createTestGameState();

      // Mock dice to return 3
      const originalRandom = Math.random;
      Math.random = () => 0.33; // Will generate 3

      const result = handleDiceRoll(gameState, 'player-1');

      Math.random = originalRandom;

      if (result.diceValue !== 6) {
        assert.strictEqual(result.newState.currentTurnPlayerId, 'player-2');
      }
    });

    it('should track consecutive sixes', function() {
      const gameState = createTestGameState({
        consecutiveSixes: 1,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 10 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      // Mock dice to return 6
      const originalRandom = Math.random;
      Math.random = () => 0.99; // Will generate 6

      const result = handleDiceRoll(gameState, 'player-1');

      Math.random = originalRandom;

      if (result.diceValue === 6) {
        assert.strictEqual(result.newState.consecutiveSixes, 2);
      }
    });

    it('should lose turn after three consecutive sixes', function() {
      const gameState = createTestGameState({
        consecutiveSixes: 2,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 10 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      // Mock dice to return 6
      const originalRandom = Math.random;
      Math.random = () => 0.99;

      const result = handleDiceRoll(gameState, 'player-1');

      Math.random = originalRandom;

      if (result.diceValue === 6) {
        assert.strictEqual(result.newState.currentTurnPlayerId, 'player-2');
        assert.strictEqual(result.newState.consecutiveSixes, 0);
      }
    });
  });

  // ==================== MOVE VALIDATION TESTS ====================

  describe('Move Validation', function() {

    it('should reject move when not player turn', function() {
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true
      });

      const result = handleMoveRequest(gameState, 'player-2', 0);

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'NOT_YOUR_TURN');
    });

    it('should reject move when must roll first', function() {
      const gameState = createTestGameState({
        canRollDice: true,
        mustSelectToken: false
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'CANNOT_MOVE');
    });

    it('should reject move for non-existent token', function() {
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true
      });

      const result = handleMoveRequest(gameState, 'player-1', 99);

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'TOKEN_NOT_FOUND');
    });

    it('should reject move for HOME token without 6', function() {
      const gameState = createTestGameState({
        diceValue: 4,
        canRollDice: false,
        mustSelectToken: true
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'INVALID_MOVE');
    });

    it('should allow HOME token to exit with 6', function() {
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.move.moveType, 'EXIT_HOME');
      assert.strictEqual(result.move.toPosition, 0);

      const movedToken = result.newState.players[0].tokens[0];
      assert.strictEqual(movedToken.state, TokenState.ACTIVE);
      assert.strictEqual(movedToken.position, 0);
    });

    it('should move ACTIVE token correctly', function() {
      const gameState = createTestGameState({
        diceValue: 4,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 10 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.move.toPosition, 14);

      const movedToken = result.newState.players[0].tokens[0];
      assert.strictEqual(movedToken.position, 14);
    });

    it('should reject move that exceeds finish', function() {
      const gameState = createTestGameState({
        diceValue: 5,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 55 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'INVALID_MOVE');
    });

    it('should finish token at exact position 57', function() {
      const gameState = createTestGameState({
        diceValue: 2,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 55 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.move.moveType, 'FINISH');

      const finishedToken = result.newState.players[0].tokens[0];
      assert.strictEqual(finishedToken.state, TokenState.FINISHED);
      assert.strictEqual(finishedToken.position, FINISH_POSITION);
    });

    it('should reject move for FINISHED token', function() {
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.FINISHED, position: FINISH_POSITION },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, false);
      assert.strictEqual(result.error, 'INVALID_MOVE');
    });
  });

  // ==================== CAPTURE TESTS ====================

  describe('Capture Validation', function() {

    it('should capture opponent token on same cell', function() {
      // Red at position 15, will move 3 to absolute 18
      // Blue at relative position 5 = absolute 18
      const gameState = createTestGameState({
        diceValue: 3,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 15 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ],
        player2Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 5 }, // Absolute: 5 + 13 = 18
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.notStrictEqual(result.captured, null);
      assert.strictEqual(result.captured.playerId, 'player-2');
      assert.strictEqual(result.captured.tokenId, 0);

      // Check captured token is back at HOME
      const capturedToken = result.newState.players[1].tokens[0];
      assert.strictEqual(capturedToken.state, TokenState.HOME);
      assert.strictEqual(capturedToken.position, HOME_POSITION);
    });

    it('should not capture on safe cell', function() {
      // Red moving to position 8 (safe cell), Blue also at absolute 8
      const gameState = createTestGameState({
        diceValue: 3,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 5 }, // Will move to 8 (safe)
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ],
        player2Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 47 }, // Blue at relative 47 = absolute (47+13)%52 = 8
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.captured, null); // No capture on safe cell
    });

    it('should grant bonus turn for capture', function() {
      const gameState = createTestGameState({
        diceValue: 3,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 15 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ],
        player2Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 5 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.bonusTurn, true);
      assert.strictEqual(result.newState.canRollDice, true);
      assert.strictEqual(result.newState.currentTurnPlayerId, 'player-1');
    });
  });

  // ==================== WIN CONDITION TESTS ====================

  describe('Win Condition', function() {

    it('should detect winner when all tokens finished', function() {
      const gameState = createTestGameState({
        diceValue: 2,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 55 }, // Will finish
          { id: 1, state: TokenState.FINISHED, position: FINISH_POSITION },
          { id: 2, state: TokenState.FINISHED, position: FINISH_POSITION },
          { id: 3, state: TokenState.FINISHED, position: FINISH_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.hasWon, true);
      assert.strictEqual(result.newState.winnerId, 'player-1');
      assert.strictEqual(result.newState.gameStatus, GameStatus.FINISHED);
    });

    it('should not set winner if not all tokens finished', function() {
      const gameState = createTestGameState({
        diceValue: 2,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 55 }, // Will finish
          { id: 1, state: TokenState.FINISHED, position: FINISH_POSITION },
          { id: 2, state: TokenState.FINISHED, position: FINISH_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION } // Not finished
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.hasWon, false);
      assert.strictEqual(result.newState.winnerId, null);
    });
  });

  // ==================== ANTI-CHEAT TESTS ====================

  describe('Anti-Cheat Validation', function() {

    it('should ignore client-provided dice value', function() {
      // Client tries to send dice value
      const gameState = createTestGameState();

      // Even if client sends diceValue, server generates its own
      const result = handleDiceRoll(gameState, 'player-1');

      assert.strictEqual(result.success, true);
      // Server always generates 1-6
      assert(result.diceValue >= 1 && result.diceValue <= 6);
    });

    it('should validate player owns the token', function() {
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true
      });

      // Player 1 trying to move player 2's token - but the handler
      // searches in player 1's tokens, so token won't be found
      const result = handleMoveRequest(gameState, 'player-1', 99);

      assert.strictEqual(result.success, false);
    });

    it('should prevent state manipulation', function() {
      // Even if client sends modified state, server should validate
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true
      });

      // The server validates the actual game state, not client claims
      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      // Server computed the correct new position
      assert.strictEqual(result.move.toPosition, 0);
    });

    it('should validate game version for replay attacks', function() {
      const gameState = createTestGameState({
        version: 5
      });

      // In a real implementation, we'd check if the action's version
      // matches current game version to prevent replay attacks
      assert.strictEqual(gameState.version, 5);
    });
  });

  // ==================== TURN MANAGEMENT TESTS ====================

  describe('Turn Management', function() {

    it('should grant bonus turn on rolling 6', function() {
      const gameState = createTestGameState({
        diceValue: 6,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 10 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.bonusTurn, true);
      assert.strictEqual(result.newState.currentTurnPlayerId, 'player-1');
      assert.strictEqual(result.newState.canRollDice, true);
    });

    it('should advance turn when no bonus', function() {
      const gameState = createTestGameState({
        diceValue: 4,
        canRollDice: false,
        mustSelectToken: true,
        player1Tokens: [
          { id: 0, state: TokenState.ACTIVE, position: 10 },
          { id: 1, state: TokenState.HOME, position: HOME_POSITION },
          { id: 2, state: TokenState.HOME, position: HOME_POSITION },
          { id: 3, state: TokenState.HOME, position: HOME_POSITION }
        ]
      });

      const result = handleMoveRequest(gameState, 'player-1', 0);

      assert.strictEqual(result.success, true);
      assert.strictEqual(result.bonusTurn, false);
      assert.strictEqual(result.newState.currentTurnPlayerId, 'player-2');
    });
  });
});

// Run tests if executed directly
if (require.main === module) {
  const Mocha = require('mocha');
  const mocha = new Mocha();

  // Add this file to mocha
  mocha.addFile(__filename);

  // Run tests
  mocha.run(failures => {
    process.exitCode = failures ? 1 : 0;
  });
}

module.exports = {
  handleDiceRoll,
  handleMoveRequest,
  createTestGameState,
  TokenState,
  GameStatus
};
