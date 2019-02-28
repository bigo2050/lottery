package me.zohar.lottery.game.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.utils.IdUtils;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.game.domain.Game;
import me.zohar.lottery.game.domain.GamePlay;
import me.zohar.lottery.game.domain.NumLocate;
import me.zohar.lottery.game.domain.OptionalNum;
import me.zohar.lottery.game.param.GameParam;
import me.zohar.lottery.game.param.GamePlayParam;
import me.zohar.lottery.game.param.NumLocateParam;
import me.zohar.lottery.game.repo.GamePlayRepo;
import me.zohar.lottery.game.repo.GameRepo;
import me.zohar.lottery.game.repo.NumLocateRepo;
import me.zohar.lottery.game.repo.OptionalNumRepo;
import me.zohar.lottery.game.vo.GamePlayVO;
import me.zohar.lottery.game.vo.GameVO;

@Service
public class GameService {

	@Autowired
	private GameRepo gameRepo;

	@Autowired
	private GamePlayRepo gamePlayRepo;

	@Autowired
	private NumLocateRepo numLocateRepo;

	@Autowired
	private OptionalNumRepo optionalNumRepo;

	@Transactional(readOnly = true)
	public List<GameVO> findAllGame() {
		List<Game> games = gameRepo.findAll(Sort.by(Sort.Order.asc("orderNo")));
		return GameVO.convertFor(games);
	}

	@Transactional(readOnly = true)
	public List<GameVO> findAllOpenGame() {
		List<Game> games = gameRepo.findByStateOrderByOrderNo(Constant.游戏状态_启用);
		return GameVO.convertFor(games);
	}

	@Transactional
	public void delGameById(String id) {
		Game game = gameRepo.getOne(id);
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(game.getGameCode());
		for (GamePlay gamePlay : gamePlays) {
			delGamePlayInner(gamePlay);
		}
		gameRepo.delete(game);
	}

	@Transactional(readOnly = true)
	public GameVO findGameById(String id) {
		Game game = gameRepo.getOne(id);
		return GameVO.convertFor(game);
	}

	@Transactional(readOnly = true)
	public List<GamePlayVO> findGamePlayByGameCode(String gameCode) {
		List<GamePlayVO> vos = new ArrayList<>();
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(gameCode);
		for (GamePlay gamePlay : gamePlays) {
			GamePlayVO vo = GamePlayVO.convertFor(gamePlay);
			vos.add(vo);
		}
		return vos;
	}

	@Transactional(readOnly = true)
	public List<GamePlayVO> findGamePlayDetailsByGameCode(String gameCode) {
		List<GamePlayVO> vos = new ArrayList<>();
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(gameCode);
		for (GamePlay gamePlay : gamePlays) {
			vos.add(GamePlayVO.buildGamePlayDetails(gamePlay));
		}
		return vos;
	}

	@Transactional(readOnly = true)
	public GamePlayVO findGamePlayDetailsById(String id) {
		GamePlay gamePlay = gamePlayRepo.getOne(id);
		return GamePlayVO.buildGamePlayDetails(gamePlay);
	}

	@Transactional
	public void updateGamePlayState(String id, String state) {
		GamePlay gamePlay = gamePlayRepo.getOne(id);
		gamePlay.setState(state);
		gamePlayRepo.save(gamePlay);
	}

	@Transactional
	public void delGamePlayById(String id) {
		GamePlay gamePlay = gamePlayRepo.getOne(id);
		delGamePlayInner(gamePlay);
	}

	@Transactional
	public void delGamePlayInner(GamePlay gamePlay) {
		Set<NumLocate> numLocates = gamePlay.getNumLocates();
		for (NumLocate numLocate : numLocates) {
			optionalNumRepo.deleteAll(numLocate.getOptionalNums());
			numLocateRepo.delete(numLocate);
		}
		gamePlayRepo.delete(gamePlay);
	}

	@ParamValid
	@Transactional
	public void addOrUpdateGame(GameParam gameParam) {
		// 新增
		if (StrUtil.isBlank(gameParam.getId())) {
			Game existGame = gameRepo.findByGameCode(gameParam.getGameCode());
			if (existGame != null) {
				throw new BizException(BizError.游戏代码已存在);
			}
			Game game = gameParam.convertToPo();
			gameRepo.save(game);
			copyGamePlay(game, gameParam.getCopyGameCode());
		}
		// 修改
		else {
			Game existGame = gameRepo.findByGameCode(gameParam.getGameCode());
			if (existGame != null && !existGame.getId().equals(gameParam.getId())) {
				throw new BizException(BizError.游戏代码已存在);
			}
			Game game = gameRepo.getOne(gameParam.getId());
			BeanUtils.copyProperties(gameParam, game);
			gameRepo.save(game);
			copyGamePlay(game, gameParam.getCopyGameCode());
		}
	}

	@Transactional
	public void copyGamePlay(Game game, String copyGameCode) {
		if (StrUtil.isBlank(copyGameCode)) {
			return;
		}
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(copyGameCode);
		for (GamePlay gamePlay : gamePlays) {
			GamePlay existGamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(game.getGameCode(),
					gamePlay.getGamePlayCode());
			if (existGamePlay != null) {
				continue;
			}

			GamePlay newGamePlay = new GamePlay();
			BeanUtils.copyProperties(gamePlay, newGamePlay);
			newGamePlay.setId(IdUtils.getId());
			newGamePlay.setGameCode(game.getGameCode());
			gamePlayRepo.save(newGamePlay);

			Set<NumLocate> numLocates = gamePlay.getNumLocates();
			for (NumLocate numLocate : numLocates) {
				NumLocate newNumLocate = new NumLocate();
				BeanUtils.copyProperties(numLocate, newNumLocate);
				newNumLocate.setId(IdUtils.getId());
				newNumLocate.setGamePlayId(newGamePlay.getId());
				numLocateRepo.save(newNumLocate);

				Set<OptionalNum> optionalNums = numLocate.getOptionalNums();
				for (OptionalNum optionalNum : optionalNums) {
					OptionalNum newOptionalNum = new OptionalNum();
					BeanUtils.copyProperties(optionalNum, newOptionalNum);
					newOptionalNum.setId(IdUtils.getId());
					newOptionalNum.setNumLocateId(newNumLocate.getId());
					optionalNumRepo.save(newOptionalNum);
				}

			}
		}
	}

	@ParamValid
	@Transactional
	public void addOrUpdateGamePlay(GamePlayParam gamePlayParam) {
		// 新增
		if (StrUtil.isBlank(gamePlayParam.getId())) {
			GamePlay existGamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(gamePlayParam.getGameCode(),
					gamePlayParam.getGamePlayCode());
			if (existGamePlay != null) {
				throw new BizException(BizError.游戏玩法代码已存在);
			}
			GamePlay gamePlay = gamePlayParam.convertToPo();
			gamePlayRepo.save(gamePlay);
			if (CollectionUtil.isEmpty(gamePlayParam.getNumLocates())) {
				return;
			}
			for (NumLocateParam numLocateParam : gamePlayParam.getNumLocates()) {
				NumLocate numLocate = numLocateParam.convertToPo();
				numLocate.setGamePlayId(gamePlay.getId());
				numLocateRepo.save(numLocate);
			}
		}
		// 修改
		else {
			GamePlay existGamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(gamePlayParam.getGameCode(),
					gamePlayParam.getGamePlayCode());
			if (existGamePlay != null && !existGamePlay.getId().equals(gamePlayParam.getId())) {
				throw new BizException(BizError.游戏玩法代码已存在);
			}
			List<NumLocate> numLocates = numLocateRepo.findByGamePlayId(gamePlayParam.getId());
			for (NumLocate numLocate : numLocates) {
				optionalNumRepo.deleteAll(numLocate.getOptionalNums());
			}
			numLocateRepo.deleteAll(numLocates);

			GamePlay gamePlay = gamePlayRepo.getOne(gamePlayParam.getId());
			delGamePlayInner(gamePlay);
			BeanUtils.copyProperties(gamePlayParam, gamePlay);
			gamePlayRepo.save(gamePlay);
			for (NumLocateParam numLocateParam : gamePlayParam.getNumLocates()) {
				NumLocate numLocate = numLocateParam.convertToPo();
				numLocate.setGamePlayId(gamePlay.getId());
				numLocateRepo.save(numLocate);
			}
		}
	}

}
