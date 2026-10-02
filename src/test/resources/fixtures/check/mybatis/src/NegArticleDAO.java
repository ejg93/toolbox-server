package egov.neg;

/** eGov DAO 꼴 — namespace 는 인터페이스가 아니라 문장 참조의 앞부분 */
public class ArticleDAO {
    Object list(Object vo) {
        return selectList("BBSArticle.selectArticleList", vo);
    }
}
